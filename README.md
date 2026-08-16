# 🚌 OmniBus — Enterprise Cloud-Native Transport Platform

[![Java](https://img.shields.io/badge/Java-17%20LTS-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x%20%2F%204.x-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-BFF%20Reactive-green.svg)](https://spring.io/projects/spring-cloud-gateway)
[![Keycloak](https://img.shields.io/badge/Keycloak-26.0.7%20IAM-blue.svg?logo=keycloak)](https://www.keycloak.org/)
[![Valkey](https://img.shields.io/badge/Valkey-8.0%20Distributed%20Lock-red.svg)](https://valkey.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x%20AMQP%20Saga-orange.svg?logo=rabbitmq)](https://www.rabbitmq.com/)
[![Angular](https://img.shields.io/badge/Angular-22%20Signals-dd0031.svg?logo=angular)](https://angular.dev/)

> **OmniBus** is an enterprise-grade, cloud-native intercity bus reservation, live 2x2 cabin seat mapping, and fleet management platform engineered with modern microservices, reactive event-driven choreography, distributed concurrency locking, and zero-trust BFF (Backend-for-Frontend) security.

---

## 📌 Architecture Overview (Pattern 3: Decentralized Embedded BFF)

```
                                 [ Browser / Angular 22 SPA ]
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
                                   [ Valkey 8+ Cluster :6379 ]
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
2. **Opaque `__Host-` Session Cookies**: The browser only holds an opaque `HttpOnly; Secure; SameSite=Lax; Path=/` session cookie (`__Host-OmniSession`).
3. **High-Performance Valkey User Info & Role Caching**: Cryptographic JWT decoding is executed **ONCE** at login/refresh via Nimbus JOSE; subsequent requests perform single $O(1)$ Valkey lookups reading `session.getUsername()` and `session.getRoles()` directly with **zero cryptographic parsing overhead**.
4. **4-Tier Interceptor & Filter Pipeline**:
   - `login.interceptor.ts` (Angular): Injects `withCredentials: true` and `X-Requested-With: XMLHttpRequest` (CSRF defense).
   - `csrfHeaderFilter` (Gateway): Rejects external state-changing requests missing browser CSRF headers.
   - `BffSessionAuthenticationFilter` (Microservice Ingress): Resolves cookies directly from Valkey and authenticates Spring `SecurityContextHolder`.
   - `FeignAuthRequestInterceptor` (Feign Egress): Relays user cookies on in-flight requests (Token Relay) and attaches M2M Service Account tokens on background/`@Scheduled` tasks.
5. **Keycloak Client Isolation**: Public `angular-client` has password grants disabled (`directAccessGrantsEnabled: false`), and confidential `internal-backend-client` has browser login disabled (`standardFlowEnabled: false`), making external Postman token forgery impossible.
6. **Valkey Distributed Concurrency Mutex (`SET NX PX 5000`)**: Prevents parallel SPA AJAX requests from executing duplicate Keycloak refresh calls.
7. **Session ID Rotation & 10s Grace Pointer**: On every token refresh, the session ID rotates atomically with a 10s forwarding grace bridge.
8. **Intrusion Detection Kill-Switch**: Replaying expired sessions triggers instant Valkey cache purging and cluster-wide Keycloak revocation.

---

## 📂 Microservices & Library Portfolio

| Service / Module | Port | Tech Stack | Core Responsibilities | Interactive Docs |
| :--- | :--- | :--- | :--- | :--- |
| **`common-security`** | *(Library)* | Spring Boot MVC + Valkey | Decentralized Embedded BFF Filter, Session Manager, Token Refresher | — |
| **`gateway`** | `8080` | Spring Cloud Gateway | 100% Stateless Pure L7 Reverse Proxy, URL Dispatch, Global CORS | — |
| **`adminservice`** | `8081` | Spring Boot 3.2 + H2 JPA | Fleet operations, coach CRUD, pricing, route schedules, revenue KPI | [Swagger UI](http://localhost:8081/swagger-ui/index.html) |
| **`bookingservice`** | `8083` | Spring Boot 3.2 + H2 JPA | Ticket reservations, cancellation, user booking history | [Swagger UI](http://localhost:8083/swagger-ui/index.html) |
| **`inventoryservice`** | `8084` | Spring Boot 3.2 + H2 JPA | Interactive 2x2 seat cabin layout, real-time seat lock, availability | [Swagger UI](http://localhost:8084/swagger-ui/index.html) |
| **`paymentservice`** | `8085` | Spring Boot 3.2 + RabbitMQ | Distributed saga payment processing, asynchronous event listener | [Swagger UI](http://localhost:8085/swagger-ui/index.html) |
| **`service-registry`** | `8761` | Netflix Eureka | Dynamic microservice discovery and heartbeat health monitoring | [Dashboard](http://localhost:8761) |
| **`keycloak`** | `8088` | Keycloak 26.0.7 IAM | OpenID Connect Identity Provider, RBAC roles (`ADMIN`, `USER`) | [Admin Console](http://localhost:8088/admin) |
| **`valkey`** | `6379` | Valkey 8.0 Alpine | Distributed concurrency mutex, zero-parsing session cache | — |
| **`rabbitmq`** | `5672` / `15672` | RabbitMQ 3 Management | Asynchronous message bus, saga choreography, event queues | [Management UI](http://localhost:15672) |
| **`angularplay`** | `4200` | Angular 22 + Signals | Responsive Single Page App, 2x2 seat picker, digital boarding passes | [Web UI](http://localhost:4200) |

---

## ⚡ Quick Start & Runbook

The project is completely self-contained with **zero global tooling requirements** (embedded Apache Maven 3.9.9 and standalone Keycloak 26.0.7 in `tools/`):

### Prerequisites
* **Java 17+ JDK** installed and configured on `PATH`.
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
| **Angular 22 Frontend** | [http://localhost:4200](http://localhost:4200) | `admin` | `admin123` | `ROLE_ADMIN` (Fleet Ops, Bookings, Analytics) |
| **Customer Booking Portal** | [http://localhost:4200/booking](http://localhost:4200/booking) | `john_doe` | `user123` | `ROLE_USER` (2x2 Seat Picker, Boarding Passes) |
| **Keycloak Admin Console** | [http://localhost:8088/admin](http://localhost:8088/admin) | `admin` | `admin` | Master Realm & IAM Configuration |
| **Eureka Dashboard** | [http://localhost:8761](http://localhost:8761) | — | — | Live Microservice Discovery Heartbeats |
| **RabbitMQ Management** | [http://localhost:15672](http://localhost:15672) | `guest` | `guest` | Message Queues, Exchanges & Channels |
| **API Gateway / BFF** | [http://localhost:8080](http://localhost:8080) | — | — | Unified Reverse Proxy Entry Point |

---

## ☸️ Evolution to Kubernetes Gateway API (Zero Code Rewrite)

When migrating from local Spring Cloud Gateway to **Kubernetes Gateway API** (Envoy Gateway, Istio, Cilium):

1. **Routing is offloaded** to pure Kubernetes `HTTPRoute` CRD manifests (Envoy Gateway proxies directly to microservice pods).
2. **Service discovery is offloaded** to native Kubernetes CoreDNS (delete `service-registry/`).
3. **Microservices remain 100% self-authenticating**:
   - Because `common-security` is injected directly into each microservice (`adminservice`, `bookingservice`, `inventoryservice`, `paymentservice`), each pod autonomously resolves sessions from Valkey in $O(1)$ time with **zero code rewrite**!
4. **Auth Endpoints (`/auth/**`)**:
   - The lightweight OIDC controller is deployed as a dedicated `auth-service` pod (or ingress route) to handle Keycloak login redirects and code exchange.

---

## 📖 Complete Technical Documentation

For complete architectural specifications, sequence diagrams, and concurrency deep-dives, open the interactive documentation:

👉 **[`DEVELOPER_GUIDE.html`](DEVELOPER_GUIDE.html)** *(Open in any web browser)*

---

*OmniBus Cloud-Native Enterprise Platform &bull; Architecture & Engineering Documentation &bull; 2026*
