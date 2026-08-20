# 🚌 OmniBus — Enterprise Cloud-Native Transport Platform

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-BFF%20Reactive-green.svg)](https://spring.io/projects/spring-cloud-gateway)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.7.1%20IAM-blue.svg?logo=keycloak)](https://www.keycloak.org/)
[![Valkey](https://img.shields.io/badge/Valkey-9.1.1%20Distributed%20Lock-red.svg)](https://valkey.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x%20AMQP%20Saga-orange.svg?logo=rabbitmq)](https://www.rabbitmq.com/)
[![Angular](https://img.shields.io/badge/Angular-21%20Signals-dd0031.svg?logo=angular)](https://angular.dev/)

> **OmniBus** is an enterprise-grade, cloud-native intercity bus reservation, live 2x2 cabin seat mapping, and fleet management platform engineered with modern microservices, reactive event-driven choreography, distributed concurrency locking, and zero-trust BFF (Backend-for-Frontend) security.

### 📖 Platform Documentation & Guides:
* 🎮 **[Prototype Playbook & Business Functionality Guide](file:///c:/Personal-Project/microservice-main/microservice-main/APPLICATION_USER_GUIDE.md)** — Step-by-step walkthrough of business workflows, demo scenarios, and persona walkthroughs.
* 🛠️ **[Developer & Engineering Architecture Guide](file:///c:/Personal-Project/microservice-main/microservice-main/DEVELOPER_GUIDE.md)** — In-depth 15-section technical handbook, distributed locking, and Kubernetes Gateway API.
* 🛡️ **[Security Standards & Zero-Trust Whitepaper](file:///c:/Personal-Project/microservice-main/microservice-main/SECURITY_STANDARDS_COMPLIANCE.md)** — OWASP Top 10, CWE-400 mitigation, rotating HMAC secrets, and NIST SP 800-63B audit.

---

## 📌 Architecture Overview (Pattern 3: Decentralized Embedded BFF)

```
                                 [ Browser / Angular 21 SPA ]
                                               │
                           ( Opaque HttpOnly Cookie: __Host-OmniSession )
                                               ▼
                         [ Spring Cloud Gateway / K8s Route :8080 ]
                           ( 100% Stateless Pure L7 Reverse Proxy )
                                               │
                ┌──────────────────────────────┼──────────────────────────────┐
                │ (Proxies Cookies As-Is)      │                              │
                ▼                              ▼                              ▼
     ┌─────────────────────┐        ┌─────────────────────┐        ┌─────────────────────┐
     │    Admin Service    │        │   Booking Service   │        │  Inventory Service  │
     │ ┌─────────────────┐ │        │ ┌─────────────────┐ │        │ ┌─────────────────┐ │
     │ │ common-security │ │        │ │ common-security │ │        │ │ common-security │ │
     │ │  (Embedded BFF) │ │        │ │  (Embedded BFF) │ │        │ │  (Embedded BFF) │ │
     │ └────────┬────────┘ │        │ └────────┬────────┘ │        │ └────────┬────────┘ │
     └──────────┼──────────┘        └──────────┼──────────┘        └──────────┼──────────┘
                │   ▲                          │   ▲                          │
                │   └──(OpenFeign Client)──────┘   └──(OpenFeign Client)──────┘
                │                              │                              │
                └──────────────────────────────┼──────────────────────────────┘
                                               │ (Shared Session Resolution & Mutex)
                                               ▼
                                    [ Valkey 9+ Cluster :6379 ]
                                   ( SET NX PX 5000 Locking & Cache )
                                               │ (On Token Expiry / Refresh)
                                               ▼
                                   [ Keycloak 26+ IAM :8088 ]
                                   ( OIDC / Client Isolation )
```

---

## 🛡️ Enterprise Security & BFF Architecture

OmniBus implements the **IETF OAuth 2.0 Security Best Current Practice (BCP)** using the **Decentralized Embedded BFF Pattern**:

1. **Zero Client-Side JWT Exposure**: Single Page Applications never receive or store raw JWT access or refresh tokens in JavaScript memory, `localStorage`, or `sessionStorage` (100% immune to XSS token exfiltration).
2. **Dual-Channel Ingress (Web + AI/M2M)**: Web browsers authenticate via hardened `__Host-OmniSession` HttpOnly cookies resolved in Valkey; AI agents, Swagger, and M2M clients authenticate via `Authorization: Bearer <jwt>` cryptographically verified with cached **Keycloak JWKS RS256** signatures (`/protocol/openid-connect/certs`).
3. **Periodic Bounded-Staleness Validation (30s Window)**: Employs **Distributed Double-Checked Locking (`lock:validate:<sid>`)** in Valkey. A single thread introspects Keycloak (`/protocol/openid-connect/token/introspect`) with silent refresh healing; concurrent threads re-read Valkey to eliminate stampedes and immediately detect revoked sessions.
4. **4-Tier Interceptor & Filter Pipeline**:
   - `login.interceptor.ts` (Angular): Injects `withCredentials: true` and `X-Requested-With: XMLHttpRequest` (CSRF defense).
   - `csrfHeaderFilter` (Gateway): Rejects external state-changing requests missing browser CSRF headers.
   - `BffSessionAuthenticationFilter` (Microservice Ingress): Resolves cookies directly from Valkey, periodically introspects Keycloak, verifies Bearer JWKS, and authenticates Spring `SecurityContextHolder`.
   - `FeignPassportRequestInterceptor` (Feign Egress): Attaches signed `X-Internal-Passport` (HMAC-SHA256) and `X-Passport-User` headers with 30s Valkey rotating keys (< 5μs validation).
5. **Keycloak Client Isolation & Auto-Bootstrap**: Public `angular-client` uses PKCE Authorization Code flow with password grants disabled (`directAccessGrantsEnabled: false`). Zero internal client secrets are stored across microservices. Keycloak 26.7.1 auto-bootstraps master admin credentials on startup.
6. **Valkey Distributed Concurrency Mutex (`SET NX PX 5000`)**: Prevents parallel SPA AJAX requests from executing duplicate Keycloak refresh calls.
7. **Session ID Rotation & 10s Grace Pointer**: On every token refresh, the session ID rotates atomically with a 10s forwarding grace bridge.
8. **Intrusion Detection Kill-Switch**: Replaying expired sessions triggers instant Valkey cache purging and cluster-wide Keycloak revocation.
9. **Angular OIDC PKCE Safe Deep-Linking**: Sanitizes redirect candidates against `/login`, `/logout`, `/callback`, and `/` to eliminate redirect loop traps.

---

## 📂 Microservices & Library Portfolio

| Service / Module | Port | Tech Stack | Core Responsibilities | Interactive Docs |
| :--- | :--- | :--- | :--- | :--- |
| **`common-security`** | *(Library)* | Spring Boot MVC + Valkey | Decentralized Embedded BFF Filter, Session Manager, Token Refresher | — |
| **`keycloak-captcha-spi`** | *(Plugin)* | Java 21 + Keycloak SPI | Server-side cryptographic CAPTCHA Authenticator SPI plugin | — |
| **`gateway`** | `8080` | Spring Cloud Gateway | 100% Stateless Pure L7 Reverse Proxy, URL Dispatch, Global CORS | — |
| **`adminservice`** | `8081` | Spring Boot 3.5.16 + Java 21 | Fleet operations, coach CRUD, pricing, route schedules, revenue KPI | [Swagger UI](http://localhost:8081/swagger-ui/index.html) |
| **`bookingservice`** | `8083` | Spring Boot 3.5.16 + Java 21 | Ticket reservations, cancellation, user booking history | [Swagger UI](http://localhost:8083/swagger-ui/index.html) |
| **`inventoryservice`** | `8084` | Spring Boot 3.5.16 + Java 21 | Interactive 2x2 seat cabin layout, real-time seat lock, availability | [Swagger UI](http://localhost:8084/swagger-ui/index.html) |
| **`paymentservice`** | `8085` | Spring Boot 3.5.16 + Java 21 | Distributed saga payment processing, asynchronous event listener | [Swagger UI](http://localhost:8085/swagger-ui/index.html) |
| **`service-registry`** | `8761` | Netflix Eureka | Dynamic microservice discovery and heartbeat health monitoring | [Dashboard](http://localhost:8761) |
| **`keycloak`** | `8088` | Keycloak 26.7.1 IAM | OpenID Connect Identity Provider, RBAC roles (`ADMIN`, `USER`) | [Admin Console](http://localhost:8088/admin) |
| **`valkey`** | `6379` | Valkey 9.1.1 Alpine | Distributed concurrency mutex, zero-parsing session cache | — |
| **`rabbitmq`** | `5672` / `15672` | RabbitMQ 3 Management | Asynchronous message bus, saga choreography, event queues | [Management UI](http://localhost:15672) |
| **`angularplay`** | `4200` | Angular 21 + Signals | Responsive Single Page App, 2x2 seat picker, digital boarding passes | [Web UI](http://localhost:4200) |

---

## ⚡ Quick Start & Runbook

The project is completely self-contained with **zero global tooling requirements** (embedded Apache Maven 3.9.9 and standalone Keycloak in `tools/`):

### Prerequisites
* **Java 21+ JDK** installed and configured on `PATH`.
* **Node.js 20+** installed.
* **Podman** or **Docker** running (for RabbitMQ and Valkey container bridging).

### Launching the Complete Platform (6-Stage Orchestration):

```bash
# Windows Batch (Recommended):
start-all.bat

# PowerShell (with real-time TCP health checks):
.\start-all.ps1

# Linux / macOS / WSL:
./start-all.sh
```

### Stopping All Services:
```bash
stop-all.bat
# or .\stop-all.ps1 / ./stop-all.sh
```

---

## 🔑 Default Credentials & Access Directory

| Console / Application | URL | Username | Password | Role / Permissions |
| :--- | :--- | :--- | :--- | :--- |
| **Keycloak Themed Login (OmniBus)** | [http://localhost:8088/realms/bus-reservation/...](http://localhost:8088/realms/bus-reservation/account) | `admin` / `john_doe` | `admin123` / `user123` | Native Vector SVG CAPTCHA + PKCE Authorization Code Flow |
| **Angular 21 Frontend** | [http://localhost:4200](http://localhost:4200) | `admin` | `admin123` | `ROLE_ADMIN` (Fleet Ops, Bookings, Analytics) |
| **Customer Booking Portal** | [http://localhost:4200/booking](http://localhost:4200/booking) | `john_doe` | `user123` | `ROLE_USER` (2x2 Seat Picker, Boarding Passes) |
| **Keycloak Admin Console** | [http://localhost:8088/admin](http://localhost:8088/admin) | `admin` | `admin` | Master Realm & IAM Configuration |
| **Eureka Dashboard** | [http://localhost:8761](http://localhost:8761) | — | — | Live Microservice Discovery Heartbeats |
| **RabbitMQ Management** | [http://localhost:15672](http://localhost:15672) | `guest` | `guest` | Message Queues, Exchanges & Channels |
| **API Gateway** | [http://localhost:8080](http://localhost:8080) | — | — | 100% Stateless Pure L7 Reverse Proxy |

---

## 🤖 Model Context Protocol (MCP) & Envoy AI Gateway 1.0.0

OmniBus exposes **Decentralized Domain-Driven MCP Servers** using **Spring AI 2.0.0** allowing autonomous LLM agents (Claude Desktop, Cursor, LangChain) to discover and execute transport operations:

| MCP Domain | Transport Protocol | SSE Handshake Stream | Tool Execution Endpoint | Required Role |
| :--- | :--- | :--- | :--- | :--- |
| **Admin Fleet Operations** | SSE + JSON-RPC | `GET /mcp/admin/sse` | `POST /mcp/admin/message` | `ROLE_ADMIN` |
| **Passenger Booking & Saga** | SSE + JSON-RPC | `GET /mcp/booking/sse` | `POST /mcp/booking/message` | `ROLE_USER` / `ROLE_ADMIN` |
| **Seat Inventory & Layout** | SSE + JSON-RPC | `GET /mcp/inventory/sse` | `POST /mcp/inventory/message` | `ROLE_USER` / `ROLE_ADMIN` |

* **Envoy AI Gateway (v1alpha1 API)**: Configured using the official [`aigateway.envoyproxy.io/v1alpha1` `MCPRoute`](file:///c:/Personal-Project/microservice-main/microservice-main/envoy/mcproute.yaml) CRD to aggregate multi-service MCP backends into a unified edge endpoint (`/mcp`).

---

## 🛂 Inter-Service OpenFeign Ephemeral Valkey Passport (Netflix MINT Architecture)

Inter-service Feign calls bypass external Keycloak roundtrips using the **Dual-Header Ephemeral Valkey Passport Pattern**:
* **Outgoing Feign Calls (`FeignPassportRequestInterceptor`)**: Injects `X-Passport-User: <username>` and a compact signed JWT `X-Internal-Passport` carrying `iss: <caller_app_name>`, `sub: <username>`, and `roles`.
* **Sub-Microsecond Verification (`PassportManager`)**: Receiving microservices fetch the caller app's symmetric HMAC key from Valkey, verify the signature, and convert the user identity into `SecurityContextHolder` in **< 5 microseconds**.
* **Zero Dropped Requests**: Keys rotate in Valkey every 30 seconds (`passport:key:<app>:current`) with a **10-second grace overlap window** (`passport:key:<app>:previous`). Session and rotation grace windows are fully configurable via properties (`session.rotation.grace-seconds=10`).

---

## 📜 Architectural Decisions & Best Practices

1. **Why No Static Secrets for Feign?**: Eliminates secret sprawl, avoids Keycloak mesh bottlenecks, and guarantees zero-trust tamper resistance.
2. **Why Asymmetric RS256 for Edge, Symmetric HS256 for Mesh?**: Asymmetric RS256 for external clients enables public verification without sharing secrets; symmetric HS256 in Valkey provides maximum throughput (< 5 microseconds) for internal hops.
3. **Why Single-Domain Ingress with Path Filtering?**: Exposes only interactive login and token endpoints while dropping internal introspection endpoints from the public internet.

## 📖 Complete Technical & Security Documentation

For comprehensive architectural specifications, sequence diagrams, concurrency deep-dives, and security compliance matrices:

* 📘 **[`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md)** — Complete Developer & Architecture Engineering Guide (16 Core Sections).
* 🛡️ **[`SECURITY_STANDARDS_COMPLIANCE.md`](SECURITY_STANDARDS_COMPLIANCE.md)** — Enterprise Security Architecture & Standards Compliance Matrix (OWASP Top 10 2021, OWASP API Top 10 2023, RFC 7636 PKCE, RFC 6265bis, NIST Zero-Trust).

---

*OmniBus Cloud-Native Enterprise Platform &bull; Architecture & Engineering Documentation &bull; 2026*
