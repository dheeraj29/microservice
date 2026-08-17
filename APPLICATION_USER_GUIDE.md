# 🚌 OmniBus Prototype Playbook & Business Functionality Guide

> **Welcome to OmniBus!** This guide walks you through how the platform works, the end-to-end business domain, and interactive scenarios you can play around with to explore every feature of the prototype.

---

## 📑 Table of Contents

1. [Executive Product Overview](#1-executive-product-overview)
2. [User Personas & Demo Credentials](#2-user-personas--demo-credentials)
3. [End-to-End Business Workflows](#3-end-to-end-business-workflows)
   * [Flow 1: Secure Login & Security Verification](#flow-1-secure-login--security-verification)
   * [Flow 2: Fleet Operations & Route Management (Admin)](#flow-2-fleet-operations--route-management-admin)
   * [Flow 3: Passenger Seat Selection & Digital Boarding Pass (Customer)](#flow-3-passenger-seat-selection--digital-boarding-pass-customer)
   * [Flow 4: Distributed Saga & Event-Driven Booking Behind the Scenes](#flow-4-distributed-saga--event-driven-booking-behind-the-scenes)
4. [🎮 Interactive Hands-On Scenarios (Try These Out!)](#4--interactive-hands-on-scenarios-try-these-out)
   * [Scenario A: The Fleet Operations Journey](#scenario-a-the-fleet-operations-journey)
   * [Scenario B: The Passenger Booking Journey](#scenario-b-the-passenger-booking-journey)
   * [Scenario C: Real-Time Seat Concurrency Collision Test](#scenario-c-real-time-seat-concurrency-collision-test)
   * [Scenario D: Zero-Trust Role-Based Access Control (RBAC) Test](#scenario-d-zero-trust-role-based-access-control-rbac-test)
5. [🖥️ Interactive Monitoring & Console Directory](#5-️-interactive-monitoring--console-directory)

---

## 1. Executive Product Overview

**OmniBus** is an enterprise cloud-native transport platform designed to handle intercity fleet management, real-time cabin seat reservations, and automated distributed payments.

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       OMNIBUS ECOSYSTEM                                          │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                  │
│   🧑‍💼 FLEET OPERATIONS (Admin)                   🧑‍🦱 PASSENGER PORTAL (Customer)                 │
│   • Live Fleet KPI Dashboard                      • Interactive 2x2 Cabin Seat Picker            │
│   • Multi-Axle Bus Fleet Roster                   • Real-Time Seat Concurrency Locking (Valkey)  │
│   • Dynamic Routes, Schedules & Fares             • Instant Digital Boarding Pass Generation     │
│   • Passenger Booking Ledger & Audit              • Asynchronous Saga Event Processing (RabbitMQ)│
│                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. User Personas & Demo Credentials

The platform comes pre-configured with two primary enterprise personas:

| Persona | Username | Password | Role | Primary Capabilities |
| :--- | :--- | :--- | :--- | :--- |
| **Fleet Operations Admin** | `admin` | `admin123` | `ROLE_ADMIN` | Fleet management, coach creation, route pricing, passenger audit, revenue KPIs |
| **Passenger / Customer** | `john_doe` | `user123` | `ROLE_USER` | Route search, 2x2 seat selection, ticket booking, digital boarding pass download |

---

## 3. End-to-End Business Workflows

### Flow 1: Secure Login & Security Verification (Keycloak Themed SSO)
1. Navigate to [`http://localhost:4200`](http://localhost:4200) and click **Login** (or navigate to any protected route like `/booking` or `/admin`).
2. The application initiates standard **OAuth2 Authorization Code Flow with PKCE (`S256`)** and redirects to Keycloak's themed login page (`http://localhost:8088/...`).
3. You will see the **OmniBus Branded Custom Login Page** served directly by Keycloak with an integrated native vector SVG CAPTCHA challenge.
4. **Features to try**:
   * Click **🔄 Refresh** next to the CAPTCHA image to see a new randomized challenge with controlled jitter and sine-wave oscillation.
   * Click **Quick-Fill Demo Credentials** (`🧑‍💼 Admin` or `🧑‍🦱 Customer`) to autofill credentials in 1 click.
   * Type the 5-character CAPTCHA code and click **Sign In to OmniBus ➔**.
   * Keycloak authenticates your credentials, verifies the CAPTCHA, and redirects back to Angular with an authorization code.
   * The backend exchanges the code for tokens and issues an opaque `__Host-OmniSession` HttpOnly cookie.

---

### Flow 2: Fleet Operations & Route Management (Admin)
When logged in as `admin`:
1. **Fleet KPI Dashboard (`/admin`)**:
   * View live statistics: **Total Fleet Count**, **Active Schedules**, **Total Revenue (₹)**, and **Average Seat Occupancy**.
2. **Fleet Roster & Bus Management**:
   * View all active coaches (e.g. *Volvo 9600 Multi-Axle Sleeper*, *Scania Metrolink AC Semi-Sleeper*).
   * Register new buses with custom registration numbers, total capacity (e.g. 40 seats), bus type, and amenities (WiFi, USB Charging, Emergency SOS).
3. **Passenger Bookings Audit (`/admin/orders`)**:
   * View real-time reservations made across the platform, including passenger names, seat numbers, fare paid, and PNR status.

---

### Flow 3: Passenger Seat Selection & Digital Boarding Pass (Customer)
When logged in as `john_doe` (or visiting `/booking`):
1. **Search Schedules**:
   * Select Origin (e.g., *Bangalore*) and Destination (e.g., *Hyderabad* or *Chennai*).
   * Filter by departure time and coach class.
2. **Interactive 2x2 Seat Layout Picker**:
   * Displays the bus interior with driver cabin, aisle divider, and window indicators.
   * **Seat States**:
     * 🟩 **Available** (White / Green outline) $\rightarrow$ Ready for selection.
     * 🟦 **Selected** (Primary Blue) $\rightarrow$ Currently chosen by you.
     * 🟥 **Occupied** (Muted Red / Disabled) $\rightarrow$ Already booked by another passenger.
3. **Checkout & Fare Summary**:
   * Displays Base Fare, GST Tax, and Total Amount Payable.
4. **Digital Boarding Pass**:
   * Upon successful booking, an airline-style digital boarding pass is rendered with:
     * **PNR Reference Number** (e.g. `OB-89421`)
     * **Seat Number** (e.g. `12A - Window`)
     * **Passenger Details & Boarding Gate**
     * **Simulated QR Verification Code**

---

### Flow 4: Distributed Saga & Event-Driven Booking Behind the Scenes

```
  [ Passenger clicks "Book Seat 12A" ]
                 │
                 ▼
  1. [ Inventory Service ] ──► Acquires Valkey Concurrency Mutex (Locks Seat 12A)
                 │
                 ▼
  2. [ Booking Service ]   ──► Creates Reservation Record (Status: PENDING)
                 │
                 ▼
  3. [ RabbitMQ Bus ]      ──► Publishes "booking.created" event to "bus-exchange"
                 │
                 ▼
  4. [ Payment Service ]   ──► Asynchronously consumes event, charges payment
                 │
                 ▼
  5. [ SAGA Completed ]    ──► Status updated to CONFIRMED, Boarding Pass generated!
```

---

## 4. 🎮 Interactive Hands-On Scenarios (Try These Out!)

### Scenario A: The Fleet Operations Journey
> **Goal**: Add a new premium coach and verify it appears in real-time.

1. Go to [`http://localhost:4200/login`](http://localhost:4200/login).
2. Click **Admin (Fleet Ops)** demo quick-fill button (`admin` / `admin123`).
3. Solve the 5-character CAPTCHA code and click **Sign In**.
4. You land on the **Fleet Operations Dashboard** (`/admin`).
5. Fill the **"Add New Bus"** form:
   * **Bus Number**: `KA-01-EQ-9999`
   * **Bus Name**: `OmniBus Volvo 9600 Luxury Sleeper`
   * **Total Capacity**: `40`
   * **Operator**: `OmniBus Express Lines`
6. Click **Register Bus to Fleet**.
7. Observe the new coach immediately added to the live fleet table and KPI counters updated!

---

### Scenario B: The Passenger Booking Journey
> **Goal**: Book a seat, trigger the distributed saga, and receive a digital boarding pass.

1. Click **Sign Out** in the top navbar (you will land on the `/logout` confirmation screen).
2. Click **Sign In Again** and choose **Customer (Booking)** quick-fill (`john_doe` / `user123`).
3. Solve the CAPTCHA and sign in.
4. You land on the **Customer Booking Portal** (`/booking`).
5. Select a bus from the schedule list and click **Select Seats**.
6. In the **2x2 Cabin Seat Picker**, click an available seat (e.g. `Seat 14B - Window`).
7. Enter Passenger Name: `John Doe`, Age: `29`, Gender: `Male`.
8. Click **Confirm & Pay (₹1,250)**.
9. Watch the live confirmation animation: the distributed saga processes payment via RabbitMQ, locks inventory in Valkey, and renders your **Digital Boarding Pass** with PNR code!

---

### Scenario C: Real-Time Seat Concurrency Collision Test
> **Goal**: Test distributed concurrency locking across two simultaneous browser sessions.

1. Open **Tab 1** in regular browser and **Tab 2** in Incognito / Private window.
2. In Tab 1, sign in as `john_doe` and go to `/booking`.
3. In Tab 2, sign in as `admin` and go to `/booking`.
4. In Tab 1, select `Seat 08A` and complete the booking.
5. In Tab 2, notice `Seat 08A` immediately turns **Red (Occupied)** and is disabled from selection!
6. If both tabs click the exact same seat at the exact same millisecond, Valkey's atomic `SETNX` lock allows only one transaction to succeed while returning a friendly *"Seat already reserved by another passenger"* alert to the other.

---

### Scenario D: Zero-Trust Role-Based Access Control (RBAC) Test
> **Goal**: Verify that security guards prevent unauthorized access without leaking JWTs.

1. While logged in as `john_doe` (Customer), try directly typing [`http://localhost:4200/admin`](http://localhost:4200/admin) in the browser address bar.
2. Notice the **AdminGuard** intercepts the navigation:
   * Recognizes `john_doe` has `ROLE_USER` but lacks `ROLE_ADMIN`.
   * Gracefully redirects you back to `/booking` with zero privilege escalation.
3. Open Browser Developer Tools (`F12`) $\rightarrow$ **Application / Storage** $\rightarrow$ **Local Storage**:
   * Notice **zero access tokens, zero refresh tokens, zero raw JWTs** in storage!
   * All authentication is bound to the opaque `HttpOnly` cookie `__Host-OmniSession`.

---

### Scenario E: User Profile & Dynamic Custom Preferences
> **Goal**: Inspect and update custom user settings (`language`, `timezone`, `homepage`, `theme`).

1. Open DevTools Console (`F12`) or inspect the `/auth/user` endpoint:
   * Returns current user identity + custom attributes:
     ```json
     {
       "authenticated": true,
       "username": "admin",
       "roles": ["ADMIN"],
       "isAdmin": true,
       "language": "en",
       "timezone": "Asia/Kolkata",
       "homepage": "/admin/dashboard",
       "theme": "dark"
     }
     ```
2. The user can dynamically update their preferences at any time via `PUT /auth/user/preferences`, syncing seamlessly across all backend instances via Valkey!

---

## 5. 🖥️ Interactive Monitoring & Console Directory

| Console / Tool | Direct URL | Default Login | Purpose / What to Inspect |
| :--- | :--- | :--- | :--- |
| **Angular 21 Web App** | [http://localhost:4200](http://localhost:4200) | `admin` / `john_doe` | Main interactive web application |
| **Keycloak Themed Login (OmniBus)** | [http://localhost:4200/login](http://localhost:4200/login) (redirects to Keycloak) | Demo Quick-Fill | Native Vector SVG CAPTCHA + PKCE Authorization Code Flow |
| **Keycloak Themed Logout** | [http://localhost:4200/logout](http://localhost:4200/logout) (redirects to Keycloak) | — | Zero-trust session termination audit screen |
| **Eureka Discovery Dashboard** | [http://localhost:8761](http://localhost:8761) | — | Live heartbeats of all 5 microservices |
| **Keycloak IAM Admin Console** | [http://localhost:8088/admin](http://localhost:8088/admin) | `admin` / `admin` | Real-time user sessions, RBAC roles, client isolation |
| **RabbitMQ Management UI** | [http://localhost:15672](http://localhost:15672) | `guest` / `guest` | Live message queues (`booking-queue`, `payment-queue`) |
| **Admin Service OpenAPI** | [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) | — | Interactive REST API testing for Fleet Ops |
| **Booking Service OpenAPI** | [http://localhost:8083/swagger-ui/index.html](http://localhost:8083/swagger-ui/index.html) | — | Interactive REST API testing for Reservations |
| **Inventory Service OpenAPI** | [http://localhost:8084/swagger-ui/index.html](http://localhost:8084/swagger-ui/index.html) | — | Interactive REST API testing for Seat Availability |
| **Payment Service OpenAPI** | [http://localhost:8085/swagger-ui/index.html](http://localhost:8085/swagger-ui/index.html) | — | Interactive REST API testing for Payment Sagas |

---

### 🚀 Starting the Platform:
To launch all services with 1 command:
```bash
# Windows:
start-all.bat

# PowerShell:
.\start-all.ps1

# Linux / macOS:
./start-all.sh
```
