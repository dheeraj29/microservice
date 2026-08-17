# 🛡️ OmniBus Enterprise Security Architecture & Standards Compliance Matrix

> **Document Classification**: Enterprise Security & Compliance Whitepaper  
> **Platform Version**: 2.0.0 (Zero-Trust Cloud-Native Architecture)  
> **Target Frameworks**: OWASP Top 10 (2021), OWASP API Security Top 10 (2023), NIST SP 800-63B, NIST SP 800-207 (Zero-Trust), OAuth 2.1 / RFC 7636, RFC 6265bis.

---

## 📑 Table of Contents

1. [Executive Security Summary](#1-executive-security-summary)
2. [OWASP Top 10 (2021) Web Security Risks & Mitigations](#2-owasp-top-10-2021-web-security-risks--mitigations)
3. [OWASP API Security Top 10 (2023) Mitigations](#3-owasp-api-security-top-10-2023-mitigations)
4. [IETF RFC & Industry Standards Compliance](#4-ietf-rfc--industry-standards-compliance)
5. [NIST Digital Identity & Zero-Trust Alignment](#5-nist-digital-identity--zero-trust-alignment)
6. [Defense-in-Depth Technical Reference Matrix](#6-defense-in-depth-technical-reference-matrix)
7. [DDoS & Cache Memory Exhaustion (CWE-400) Mitigations](#7-ddos--cache-memory-exhaustion-cwe-400-mitigations)

---

## 1. Executive Security Summary

The **OmniBus Cloud-Native Enterprise Platform** adheres to a **Zero-Trust Architecture (ZTA)** that enforces defense-in-depth across all system boundaries:

```
[ Angular 21 SPA ]
        │  ▲
        │  │  HttpOnly, Secure, SameSite=Lax Cookie (__Host-OmniSession)
        ▼  │  [NO Raw JWTs in JavaScript / localStorage]
[ Edge Gateway / BFF ] ──(PKCE S256 + State)──► [ Keycloak 26+ IAM ]
        │                                                │
        │ (Opaque Session Cache)                         │ (M2M Service Account JWTs)
        ▼                                                ▼
[ Valkey 8+ Cluster ] ◄──────────────────────────────────┘
        │
        │ OpenFeign + M2M Bearer Token (RSA Signed JWKS)
        ▼
[ Core Microservices ] ──(AMQP Events)──► [ RabbitMQ Cluster ]
```

### Core Security Tenets:
* **Token Isolation**: Single Page Applications (SPA) never handle, inspect, or store raw JSON Web Tokens (JWTs), eliminating JavaScript token theft via Cross-Site Scripting (XSS).
* **Decentralized Embedded BFF**: Microservices independently validate opaque session cookies against a shared high-performance Valkey cache, eliminating monolithic Gateway CPU bottlenecks.
* **Cryptographic Authorization Code Exchange (PKCE)**: Mandatory RFC 7636 SHA-256 (`S256`) code challenges for both Swagger UI and BFF authentication orchestrators.
* **Dual-Mode Microservice Mesh**: Inter-service OpenFeign calls automatically relay the active user's session context for interactive requests, while asynchronously falling back to cryptographically signed OAuth 2.0 Client Credentials (M2M) for `@Scheduled` background jobs and asynchronous workers.

---

## 2. OWASP Top 10 (2021) Web Security Risks & Mitigations

### 🔴 A01:2021 – Broken Access Control

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Unauthorized Endpoint Access** | Role-Based Access Control (RBAC) enforced via `@PreAuthorize("hasRole('ADMIN')")` and `@PreAuthorize("hasRole('USER')")` at controller and method levels. | [`AdminController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/adminservice/src/main/java/com/da/demo/adminservice/controller/AdminController.java), [`BookingController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/bookingservice/src/main/java/com/da/demo/bookingservice/controller/BookingController.java) |
| **Actuator Information Exposure** | Sensitive actuator endpoints (`/env`, `/heapdump`, `/beans`, `/configprops`) are permanently disabled. Only `/health`, `/info`, and `/prometheus` are exposed. | [`application.properties`](file:///c:/Personal-Project/microservice-main/microservice-main/adminservice/src/main/resources/application.properties) (`management.endpoints.web.exposure.include=health,info,prometheus`) |
| **User Attribute Privilege Escalation** | Profile & preference updates (`language`, `theme`, etc.) use the **User's Active Bearer Token** via Keycloak's Account API (`/realms/{realm}/account`). Eliminates broad admin tokens (`manage-users`), guaranteeing users can only modify their own attributes. | [`KeycloakAuthService.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/service/KeycloakAuthService.java), [`BffAuthController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/controller/BffAuthController.java) |
| **Direct Token Endpoint Exposure** | Kubernetes Gateway API (`HTTPRoute`) isolates Keycloak's token minting endpoint (`/protocol/openid-connect/token`) to internal ClusterIP. Only public interactive UI endpoints (`/auth`, `/logout`, `/broker/`) are routeable from the edge. | [`envoy/httproute.yaml`](file:///c:/Personal-Project/microservice-main/microservice-main/envoy/httproute.yaml) |

---

### 🔴 A02:2021 – Cryptographic Failures

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Authorization Code Interception** | Mandatory **PKCE (Proof Key for Code Exchange - RFC 7636)** using `java.security.SecureRandom` (32 bytes entropy) and SHA-256 hash (`code_challenge_method=S256`). | [`PkceUtil.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/service/PkceUtil.java), [`BffAuthController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/controller/BffAuthController.java) |
| **Hardcoded Secrets & Plaintext Keys** | All Keycloak client secrets and database passwords use externalized environment variables with safe dev fallbacks (`${KEYCLOAK_INTERNAL_SECRET:...}`). | [`application.properties`](file:///c:/Personal-Project/microservice-main/microservice-main/adminservice/src/main/resources/application.properties), [`compose.yaml`](file:///c:/Personal-Project/microservice-main/microservice-main/compose.yaml) |
| **Cookie Man-in-the-Middle** | Session cookies utilize `__Host-` prefix with `Secure`, `HttpOnly`, `SameSite=Lax`, and `Path=/`. Dynamic HTTPS detection via `X-Forwarded-Proto` header. | [`BffAuthController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/controller/BffAuthController.java) |
| **Forged / Tampered JWTs** | Microservices validate RSA-256 signatures against Keycloak's public JWKS endpoint (`/protocol/openid-connect/certs`). | [`common-security/pom.xml`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/pom.xml) (`spring-boot-starter-oauth2-resource-server`) |

---

### 🔴 A03:2021 – Injection

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **SQL Injection (SQLi)** | 100% of database interactions use Spring Data JPA / Hibernate parameterized queries and ORM repository interfaces. Zero raw SQL string concatenation. | [`BusDetailRepository.java`](file:///c:/Personal-Project/microservice-main/microservice-main/adminservice/src/main/java/com/da/demo/adminservice/repository/BusDetailRepository.java), [`BookingRepository.java`](file:///c:/Personal-Project/microservice-main/microservice-main/bookingservice/src/main/java/com/da/demo/bookingservice/repository/BookingRepository.java) |
| **Open Redirect Injection** | Login return targets are strictly sanitized (`sanitizeRedirectUrl`) ensuring destination paths are relative (`/`) and rejecting protocol-relative phishing URLs (`//evil.com`) or backslashes (`\`). | [`BffAuthController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/controller/BffAuthController.java) |

---

### 🔴 A04:2021 – Insecure Design

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Client-Side Token Storage Anti-Pattern** | Implemented the **Decentralized Embedded BFF** pattern. JWT tokens are stored exclusively in Valkey memory. The browser receives only an unguessable opaque session ID. | [`DistributedSessionManager.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/session/DistributedSessionManager.java), [`DEVELOPER_GUIDE.md`](file:///c:/Personal-Project/microservice-main/microservice-main/DEVELOPER_GUIDE.md#3-decentralized-embedded-bff-pattern--valkey-caching) |
| **Token Refresh Race Conditions (Stampede)** | Distributed mutex locking in Valkey (`SET mutex:refresh:<sid> NX PX 5000`) paired with 10-second graceful pointer redirection (`pointer:<oldSid> = <newSid>`) ensures concurrency-safe single-thread token renewal. | [`DistributedSessionManager.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/session/DistributedSessionManager.java) |
| **Cascading Microservice Outages** | Circuit Breakers (`@CircuitBreaker`) and automatic Retries (`@Retry`) via Resilience4j with graceful fallback methods. | [`BookingController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/bookingservice/src/main/java/com/da/demo/bookingservice/controller/BookingController.java) |

---

### 🔴 A05:2021 – Security Misconfiguration

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Wildcard CORS (`Access-Control-Allow-Origin: *`)** | Removed all ad-hoc `@CrossOrigin` annotations. CORS is centrally managed at the Gateway edge with strict explicit origins (`http://localhost:4200`) and `allowCredentials: true`. | [`gateway/src/main/resources/application.yml`](file:///c:/Personal-Project/microservice-main/microservice-main/gateway/src/main/resources/application.yml) |
| **Default Password / Credential Stuffing** | Direct Access Grants (ROPC) permanently disabled in Keycloak realm definition (`directAccessGrantsEnabled: false`). Keycloak rejects username/password HTTP POST token grants. | [`keycloak/realm-export.json`](file:///c:/Personal-Project/microservice-main/microservice-main/keycloak/realm-export.json) |
| **Excessive Scope / PII Exposure Attack Surface** | Purged non-essential client scopes (`phone`, `address`, `acr`, `organization`, `microprofile-jwt`), leaving only strictly essential scopes (`openid`, `profile`, `email`, `roles`, `web-origins`). | [`keycloak/realm-export.json`](file:///c:/Personal-Project/microservice-main/microservice-main/keycloak/realm-export.json) |
| **Stack Trace Leakage** | Standardized RFC 7807 Problem Details via centralized `GlobalExceptionHandler`. Internal exception stack traces are suppressed from client responses. | [`GlobalExceptionHandler.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/exception/GlobalExceptionHandler.java) |

---

### 🔴 A06:2021 – Vulnerable and Outdated Components

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Unpatched Dependencies / CVEs** | Upgraded platform to latest production baseline: **Spring Boot `3.5.16`**, **Spring Cloud `2025.0.3`**, **SpringDoc OpenAPI `2.9.0`**, **Nimbus JOSE JWT `10.9.1`**, and **ModelMapper `3.2.6`**. | [`pom.xml`](file:///c:/Personal-Project/microservice-main/microservice-main/pom.xml) |
| **Version Drift Across Services** | Centralized root multi-module parent POM (`omnibus-parent:1.0.0`) manages all dependency versions centrally via `<dependencyManagement>`. | [`pom.xml`](file:///c:/Personal-Project/microservice-main/microservice-main/pom.xml) |

---

### 🔴 A07:2021 – Identification and Authentication Failures

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Session Fixation / Replay Attacks** | **Refresh Token Rotation (RTR)**: Keycloak invalidates refresh tokens upon single use (`Revoke Refresh Token: true`). In addition, Valkey archives rotated session IDs (`revoked_archive:<sessionId>`) to detect hijacked reuse attempts. | [`keycloak/realm-export.json`](file:///c:/Personal-Project/microservice-main/microservice-main/keycloak/realm-export.json), [`DistributedSessionManager.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/session/DistributedSessionManager.java) |
| **Session Hijacking via Stolen Cookie** | Client Fingerprinting binds the client's network address/host to the Valkey session record during creation. | [`DistributedSessionManager.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/session/DistributedSessionManager.java) |
| **Long-Lived Leaked Access Tokens** | Access tokens are tuned to **5 minutes (300s)**. SSO Session idle timeout is tuned to **30 minutes**, perfectly aligned with Valkey's sliding window cache TTL. | [`DEVELOPER_GUIDE.md`](file:///c:/Personal-Project/microservice-main/microservice-main/DEVELOPER_GUIDE.md#4-keycloak-architecture-configuration--best-practices) |
| **CSRF in Login Flow** | Ephemeral `state` UUID tokens stored in Valkey (`pkce:state:<state>`) validate that the login callback originates from the exact client instance that started it. | [`BffAuthController.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/controller/BffAuthController.java) |

---

### 🔴 A08:2021 – Software and Data Integrity Failures

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Untrusted Message Payloads** | Asynchronous RabbitMQ messages use structured JSON serialization with Jackson `JavaTimeModule` and typed DTO contracts (`BookingModel`, `PaymentModel`). | [`RabbitMQConfig.java`](file:///c:/Personal-Project/microservice-main/microservice-main/bookingservice/src/main/java/com/da/demo/bookingservice/config/RabbitMQConfig.java) |
| **M2M Impersonation** | Internal microservice REST calls cannot forge headers; they must supply a valid JWT obtained via OpenFeign's `FeignAuthRequestInterceptor` using Keycloak's Client Credentials Grant. | [`FeignAuthRequestInterceptor.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/feign/FeignAuthRequestInterceptor.java) |

---

### 🔴 A09:2021 – Security Logging and Monitoring Failures

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Undetected Security Incidents** | Comprehensive SLF4J logging across all security filters (`BffSessionAuthenticationFilter`), token exchange handlers, and session lifecycle managers. | [`BffSessionAuthenticationFilter.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/filter/BffSessionAuthenticationFilter.java) |
| **Metric Scraping & Health Auditing** | Actuator Prometheus endpoint (`/actuator/prometheus`) enabled for continuous infrastructure telemetry and alerting. | [`application.properties`](file:///c:/Personal-Project/microservice-main/microservice-main/adminservice/src/main/resources/application.properties) |

---

### 🔴 A10:2021 – Server-Side Request Forgery (SSRF)

| Threat Scenario | OmniBus Platform Mitigation | Code & Configuration Reference |
| :--- | :--- | :--- |
| **Arbitrary Outbound HTTP Requests** | OpenFeign HTTP clients strictly resolve service instances via dynamic Netflix Eureka service discovery (`@FeignClient(name = "inventoryservice")`). Endpoints cannot be overridden by user-supplied URLs. | [`InventoryClient.java`](file:///c:/Personal-Project/microservice-main/microservice-main/bookingservice/src/main/java/com/da/demo/bookingservice/client/InventoryClient.java) |

---

## 3. OWASP API Security Top 10 (2023) Mitigations

```
┌───────────────────────────────────────────────┬────────────────────────────────────────────────────────────────────────┐
│ OWASP API Security Top 10 (2023)              │ OmniBus Architectural Defense                                          │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API1:2023 Broken Object Level Authorization   │ Controller validation verifying authenticated user ownership on ticket │
│                                               │ cancellations and booking queries.                                     │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API2:2023 Broken Authentication               │ Keycloak 26+ OIDC, PKCE (S256), Refresh Token Rotation, Valkey state.  │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API3:2023 Broken Object Property Level Auth   │ Strict separation of Request DTOs and Database Entities via ModelMapper│
│                                               │ preventing mass assignment / over-posting vulnerabilities.             │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API4:2023 Unrestricted Resource Consumption   │ Resilience4j circuit breakers, short HTTP timeouts (3s), and Valkey   │
│                                               │ memory eviction guarantees (4-tier TTL lifecycle).                     │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API5:2023 Broken Function Level Authorization │ Spring Security `@PreAuthorize` role enforcement on all admin methods. │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API6:2023 Unrestricted Access to Sensitive    │ Concurrent seat booking protected by distributed Redis locking and    │
│           Business Flows                      │ Saga transaction choreography via RabbitMQ.                            │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API7:2023 Server Side Request Forgery (SSRF)  │ Eureka service name discovery; static backend reference routing.       │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API8:2023 Security Misconfiguration           │ Centralized Gateway CORS, Actuator restriction, disabled ROPC.         │
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API9:2023 Improper Inventory Management       │ OpenAPI 3.0 / Swagger UI documentation with automatic schema contracts.│
├───────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────┤
│ API10:2023 Unsafe Consumption of APIs         │ M2M Client Credentials with Valkey Bearer token caching and validation.│
└───────────────────────────────────────────────┴────────────────────────────────────────────────────────────────────────┘
```

---

## 4. IETF RFC & Industry Standards Compliance

### 📜 1. RFC 7636 – Proof Key for Code Exchange (PKCE)
* **Compliance Level**: **Full (Strict S256)**
* **Implementation**:
  * `code_verifier`: 32 bytes (256 bits) of cryptographic entropy generated via `java.security.SecureRandom`, encoded as a 43-character unpadded Base64URL string.
  * `code_challenge`: Calculated via `Base64URL(SHA-256(ASCII(code_verifier)))` without padding (`code_challenge_method=S256`).
  * Enforced across **Swagger UI** (`springdoc.swagger-ui.oauth.use-pkce-with-authorization-code-grant=true`) and **Gateway BFF** (`BffAuthController`).

### 📜 2. RFC 6265bis & RFC 6749 – Cookie & OAuth 2.1 Standard
* **Compliance Level**: **Full**
* **Implementation**:
  * Cookie name: `__Host-OmniSession` (Enforces HTTPS, root path `/`, and domain restriction per RFC 6265bis).
  * Cookie directives: `HttpOnly; Secure; SameSite=Lax; Path=/`.
  * Single-use authorization codes exchanged on back-channel over private TLS.

### 📜 3. RFC 7807 – Problem Details for HTTP APIs
* **Compliance Level**: **Full**
* **Implementation**:
  * Structured JSON error responses containing `type`, `title`, `status`, `detail`, `instance`, and `timestamp`.
  * Implemented centrally in [`GlobalExceptionHandler.java`](file:///c:/Personal-Project/microservice-main/microservice-main/common-security/src/main/java/com/da/demo/security/exception/GlobalExceptionHandler.java).

### 📜 4. RFC 7519 – JSON Web Token (JWT)
* **Compliance Level**: **Full**
* **Implementation**:
  * Digital signatures verified using RSA-256 (`RS256`) against Keycloak JWKS (`/protocol/openid-connect/certs`).
  * Custom claims parsing for roles (`realm_access.roles`) and identity attributes (`preferred_username`, `email`, `sub`).

---

## 5. NIST Digital Identity & Zero-Trust Alignment

### 🏛️ NIST SP 800-63B (Digital Identity Guidelines)
* **Authenticator Assurance Level 2 (AAL2) Ready**:
  * Deprecation of Resource Owner Password Credentials (ROPC).
  * Short-lived access tokens (5 minutes) with mandatory single-use refresh token rotation.
  * Front-channel interactive logout with IdP session invalidation.

### 🏛️ NIST SP 800-207 (Zero-Trust Architecture)
* **Micro-Segmentation**:
  * Every microservice acts as its own Policy Enforcement Point (PEP) via `BffSessionAuthenticationFilter` and `common-security`.
  * Service-to-service calls require mutual identity verification using Client Credentials M2M Bearer tokens.
* **Continuous Verification**:
  * Client fingerprints (IP/Host) are checked on every session lookup.
  * Sliding-window session expiration (30 minutes) requiring active user interaction.

---

## 6. Defense-in-Depth Technical Reference Matrix

```
┌──────────────────────────────┬────────────────────────────────────┬────────────────────────────────────────────────────────┐
│ Security Threat              │ Architectural Defense Layer        │ Primary Source Code Link                               │
├──────────────────────────────┼────────────────────────────────────┼────────────────────────────────────────────────────────┤
│ XSS Token Extraction         │ HttpOnly Opaque Cookie (__Host-)   │ BffAuthController.java                                 │
│ Auth Code Interception       │ PKCE SHA-256 (RFC 7636)            │ PkceUtil.java                                          │
│ CSRF / State Tampering       │ Random UUID State + Valkey Check   │ BffAuthController.java                                 │
│ Open Redirect Phishing       │ Relative URI Path Sanitization     │ BffAuthController.java (sanitizeRedirectUrl)           │
│ Deep-Link Navigation Loss    │ State-Bound Target URL In Valkey   │ DistributedSessionManager.java (PkceStateRecord)       │
│ Token Refresh Race Condition │ Valkey Mutex + Grace Pointer (10s) │ DistributedSessionManager.java (resolveSession)        │
│ Stolen Refresh Token Replay  │ Keycloak RTR + Revoked Archive     │ keycloak/realm-export.json                             │
│ Brute Force / Auto Cred Stuff│ Server-Side CAPTCHA + Brute Force  │ ValkeyCaptchaAuthenticator.java & Keycloak Realm       │
│ Microservice Impersonation   │ M2M Client Credentials Grant       │ FeignAuthRequestInterceptor.java                       │
│ Gateway Memory Exhaustion    │ 4-Tier Valkey TTL Lifecycle        │ DistributedSessionManager.java                         │
│ Service Cascading Failures   │ Resilience4j Circuit Breakers      │ BookingController.java                                 │
│ Gateway Edge Penetration     │ Kubernetes HTTPRoute Whitelist     │ envoy/httproute.yaml                                   │
└──────────────────────────────┴────────────────────────────────────┴────────────────────────────────────────────────────────┘
```

---

## 7. DDoS & Cache Memory Exhaustion (CWE-400) Mitigations

Under distributed denial-of-service (DDoS) or high-concurrency bot attacks, caching layers (Valkey/Redis) are susceptible to **Cache Exhaustion (CWE-400)** where automated scripts flood generation endpoints to consume host RAM. OmniBus mitigates this using a **4-tier Defense-in-Depth model**:

### 🛡️ Tier 1: Gateway IP & Endpoint Rate Limiting (Token Bucket / Sliding Window)
* **`/auth/captcha`**: Limited to **15 requests/min per IP** (HTTP 429 Too Many Requests).
* **`/auth/login`**: Limited to **5 attempts/min per IP** to prevent automated credential stuffing (OWASP OAT-007) and brute force attacks (OWASP OAT-008).
* **Declarative Specification**: Specified in [`envoy/ratelimit-policy.yaml`](file:///c:/Personal-Project/microservice-main/microservice-main/envoy/ratelimit-policy.yaml) for Kubernetes Gateway API / Envoy.

### 🧮 Tier 2: Keycloak Server-Side CAPTCHA Authenticator SPI (`keycloak-captcha-spi`)
* **100% Server Enforced**: The custom `ValkeyCaptchaAuthenticator` SPI runs directly inside Keycloak's server-side authentication pipeline. API/curl direct POST attempts cannot bypass verification.
* **Valkey Cluster Secret Sync**: The cluster HMAC signing key is synchronized across all Keycloak pods via Valkey (`keycloak:captcha:cluster_secret`) or external environment variable (`KEYCLOAK_CAPTCHA_SECRET`), ensuring cross-instance compatibility.
* **Distributed Single-Use Replay Protection**: Token signatures are atomically recorded in Valkey upon submission (`SET captcha:used:<sig> 1 EX 120 NX`), preventing attackers from replaying a captured CAPTCHA token across different Keycloak nodes.
* **Ephemeral Cryptographic HMAC**: Challenges are signed with 120s time-bounded HMAC-SHA256 signatures with resilient fallback if Valkey is temporarily offline.
* **OmniBus Custom Theme**: High-DPI vector SVG challenge with 1-click refresh 🔄 and quick-fill demo credentials.

### 🔒 Tier 3: Keycloak Native Brute Force Detection & Account Lockout
* **Active Protection**: `bruteForceProtected: true` activated on `bus-reservation` realm.
* **Failure Factor**: Max **5 failed password attempts** before account is temporarily locked.
* **Progressive Delay & Lockout**: Imposes a 2-second delay after initial failures and locks user accounts for **15 minutes (`maxFailureWaitSeconds: 900`)** upon threshold violation.

### ⚙️ Tier 4: Valkey Engine Guardrails & Eviction Policies
* **Hard Memory Ceiling**: Valkey is bounded by `--maxmemory 256mb` (or `512mb` in cluster mode), preventing Linux OOM killer invocation.
* **`volatile-lru` Eviction**: Automatically purges expiring keys under memory pressure, preserving platform availability.
* **Multi-Tier Strict TTLs**: User sessions (30m), token rotation grace pointers (10s), distributed mutex locks (5s).

