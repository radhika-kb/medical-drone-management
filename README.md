# Intelligent Medical Drone Delivery System
## Bengaluru 4-Zone + Central Hub Architecture

> **Architecture:** Spring Boot 3.3 (Java 21 / VS Code) + MySQL Workbench 8.0+ + Python FastAPI Multi-Agent AI + React 18 + WebSocket STOMP + Leaflet Maps

---

## 1. System Overview

The Intelligent Medical Drone Delivery System is an autonomous multi-tier platform for coordinating urgent medical deliveries (blood, organs, vaccines, antivenom) across Bengaluru. The operational airspace is partitioned into four operational zones linked to one Central Command Hub:

- **Central Hub**: Global Fleet Coordination (Cubbon Park / Nrupathunga Rd), fallback resource allocation, system-wide analytics, and no-fly zone monitoring.
- **North Zone**: Hebbal, Yelahanka, Manyata Tech Park, Sahakara Nagar, Mathikere.
- **East Zone**: Whitefield, KR Puram, Marathahalli, Varthur, ITPL corridor.
- **South Zone**: Electronic City, Koramangala, BTM Layout, Bannerghatta Road, Jayanagar.
- **West Zone**: Rajajinagar, Peenya Industrial Area, Malleshwaram, Yeshwanthpur, KR Market.

---

## 2. Key Capabilities Implemented

1. **MySQL Workbench & MySQL 8 Compatibility**:
   - Converted database configuration to MySQL (`com.mysql.cj.jdbc.Driver`).
   - Dedicated `sql/init-mysql.sql` script ready to run in **MySQL Workbench** with one click (creates database `medical_drone_db` with all tables, constraints, and initial data).
   - `application-mysql.yml` configured with standard localhost:3306 and automatic schema updating.

2. **Hospital onboarding with dynamic locations**:
   - Seeded facility directory rows begin pending, inactive, and without coordinates.
   - Admin enters an address; Spring Boot calls the configured geocoding provider and returns Bengaluru matches with server-derived zones.
   - Admin selects a match, confirms or adjusts the Leaflet pin, and submits. Spring Boot validates Bengaluru bounds, recalculates the zone, and saves the location.
   - A hospital cannot be approved or participate in verified-hospital workflows until its location and zone are saved.

4. **Live Map, Drone Tracking & Bengaluru No-Fly Zones Geofencing**:
   - Live interactive radar map on **Dashboard** and **Mission Details** using Leaflet.
   - Displays all 4 operational zone corridors with colored overlays.
   - Central Command Hub marker with animated radar pulse.
   - Real-time fleet drone tracking markers with battery %, status, and flight corridors.
   - **5 Bengaluru No-Fly Zones with Red Circular Geofences**:
     1. *HAL Bangalore Airport Restricted Airspace* (4.5 km buffer)
     2. *Yelahanka Air Force Station Military Airspace* (5.0 km buffer)
     3. *Kempegowda International Airport (KIA Devanahalli)* (7.5 km buffer)
     4. *Raj Bhavan & Vidhana Soudha High-Security VVIP Geofence* (1.8 km buffer)
     5. *Jakkur Aerodrome Flight Training Corridor* (2.5 km buffer)
   - Route Agent automatically computes detour waypoints around these no-fly zones.

---

## 3. Technology Stack & Responsibilities

| Service | Technology | Port | Responsibilities |
|---|---|---|---|
| **Core Backend** | Spring Boot 3.3 / Java 21 (VS Code) | `8080` | System of record, Spring Security 6 (JWT + Refresh tokens), RBAC, zone resolution, NoFlyZoneController, `@Transactional` dispatch, and STOMP WebSocket telemetry. |
| **Relational Database** | MySQL Workbench 8.0+ / MySQL | `3306` | Normalized relational schema (`users`, `drone_zones`, `central_hubs`, `drones`, `medical_centers`, `medical_requests`, `missions`, `routes`, `no_fly_zones`, `agent_decisions`, `audit_logs`). |
| **AI Decision Service** | Python FastAPI / Scikit-Learn / LangGraph | `8000` | Stateless agent orchestration; three-tier allocation, A* airspace route search with no-fly-zone checks, weather and ETA model inference, and safety recommendations. |
| **Frontend** | React 18 / Vite / Leaflet | `5173` | Bengaluru live radar map, 4-zone fleet filter, admin hospital onboarding with pin confirmation, mission dispatch with map preview, and live telemetry tracking. |

### External data APIs

- **Map tiles:** Leaflet loads OpenStreetMap raster tiles at `https://tile.openstreetmap.org/{z}/{x}/{y}.png`; this needs no API key. Keep the visible OpenStreetMap attribution on the map. For deployments that need a service-level availability guarantee or commercial use, configure a suitable tile provider with `VITE_MAP_TILE_URL` in `frontend/.env`.
- **Weather:** The Python weather agent calls Open-Meteo's current weather endpoint for the requested coordinates, asks for Bengaluru local time and km/h wind units, and records the source with the observation. No API key is needed for the free non-commercial tier. It is subject to Open-Meteo's usage limits and terms; use a paid plan for commercial use.
- **Geocoding:** Hospital addresses are geocoded by Spring Boot. The provider is configured with `GEOCODING_PROVIDER_URL` and `GEOCODING_USER_AGENT`; the default is OpenStreetMap Nominatim. The backend caches address lookups, limits requests to one per second, and returns results inside the Bengaluru search bounds. Admins must confirm or move the map pin before save. Public Nominatim is intended for moderate user-triggered use and requires an identifying User-Agent and visible OpenStreetMap attribution; use a commercial provider or self-hosted service if those limits do not fit deployment needs ([Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/)).
- **Payments and optional AI explanations:** Razorpay credentials and the optional Groq key are external account credentials. They must be supplied through environment variables; the placeholder Razorpay values are not working credentials, and Groq is not required for core AI-agent decisions.

---

## 4. Phase 0 Baseline: Configuration and Startup

### Configuration

Copy `.env.example` to `.env` in the project root and replace the local MySQL password and JWT secret. Keep `.env` private; it is ignored by Git. The example values are for a local development stack only. Docker Compose reads this file for MySQL and Spring Boot; a Spring process started from `backend-spring` imports `../.env`. For frontend overrides, copy `frontend/.env.example` to `frontend/.env`; Vite variables are public, so never put credentials there. Configure `GEOCODING_USER_AGENT` with a real project contact before using public Nominatim. Optional `GROQ_API_KEY` enables generated explanations; `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` enable the external payment gateway. Weather observations come from Open-Meteo.

### Start services

With Docker Desktop running, start the stateful services and backends from the project root:

```powershell
Copy-Item .env.example .env
# Edit .env and set unique local DB_PASSWORD and JWT_SECRET values.
docker compose up --build -d
```

Start the frontend in a separate terminal:

```powershell
cd frontend
npm run dev
```

Docker Compose starts MySQL, Redis, FastAPI, and Spring Boot. MySQL initializes `medical_drone_db` from `sql/init-mysql.sql` the first time its named volume is created. Subsequent starts preserve the database in `mysql_data`. The frontend proxies `/api` and `/ws` to Spring Boot.

Check container startup with `docker compose ps`. The FastAPI health response reports whether all four packaged ML artifacts loaded. Spring Boot's `/api/ai/health` reports its AI service connection and requires a bearer token. Verify the WebSocket by opening the frontend tracking view for an active mission; the browser console should show a STOMP connection to `/ws` and updates arrive on `/topic/missions/{id}`. The local build and health checks do not simulate a physical drone flight.

### Service URLs and API entry points

| Service | Local URL / port | Health or entry point |
|---|---|---|
| React + Vite | `http://localhost:5173` | UI; proxies `/api` and `/ws` to Spring Boot |
| Spring Boot | `http://localhost:8080` | REST API under `/api`; AI integration status at `/api/ai/health` |
| Spring WebSocket / STOMP | `http://localhost:8080/ws` | SockJS or WebSocket handshake; mission topic `/topic/missions/{id}` |
| FastAPI decision service | `http://localhost:8000` | Swagger UI `/docs`; model status `/internal/ai/health`; workflow `/internal/ai/workflow` (`/internal/ai/decision` compatibility alias) |
| MySQL 8 | `localhost:3306` | Database `medical_drone_db`; user `root` in the Compose development stack |
| Redis 7 | `localhost:6379` | Spring cache and lock support |

Spring REST route groups include `/api/auth` (login, registration, refresh), `/api/users`, `/api/medical-requests` (also aliased as `/api/requests`), `/api/medical-centers` (also `/api/hospitals`), `/api/medical-items`, `/api/inventory`, `/api/missions`, `/api/drones`, `/api/zones`, `/api/no-fly-zones`, `/api/supply-responses`, `/api/payments`, `/api/pricing-rules`, `/api/notifications`, `/api/analytics`, and `/api/ai` (model status, demand forecasts, inventory-risk alerts). Most routes require a bearer token; use `POST /api/auth/login` to obtain one.

### Phase 1 database schema

The MySQL schema and JPA mappings now use the design names `roles`, `users`, `refresh_tokens`, `central_hubs`, `zones`, `hospitals`, `medical_items`, `hospital_inventory`, `medical_requests`, `supply_responses`, `airborne_units`, `missions`, `routes`, `route_waypoints`, `no_fly_zones`, `weather_observations`, `agent_decisions`, `demand_predictions`, `eta_predictions`, `mission_anomalies`, `payments`, `notifications`, and `audit_logs`. `mission_tracking` remains as the telemetry history table used by the existing application. The prior `drone_zones`, `medical_centers`, and `drones` tables were renamed in place so their rows and foreign keys remain intact.

`pricing_rules` replaces the hardcoded delivery pricing constants. Its rule types are `BASE` (base fee and per-kilometer/per-kilogram rates), `CATEGORY` (medical category surcharge), and `URGENCY` (urgency multiplier). Existing defaults preserve the former price calculation: â‚¹450 base, â‚¹40/km, â‚¹80/kg, with â‚¹500 organ, â‚¹200 blood, and â‚¹150 antivenom surcharges; critical and high urgency multipliers are 1.5 and 1.25. Admins can list, create, update, and deactivate rules through `GET/POST /api/pricing-rules`, `PUT /api/pricing-rules/{id}`, and `DELETE /api/pricing-rules/{id}`. These routes require an admin bearer token, and changes are recorded in `audit_logs`.

For an existing database, stop the backend, apply any needed `2026-10-04` data/request migrations first (they use the old table names), then apply `sql/migrations/2026-10-05-finalize-schema-and-pricing.sql` before starting the Phase 1 backend. The finalization migration preserves rows, is safe to rerun, and seeds defaults only when their rule codes do not already exist. Fresh databases get the finalized schema and defaults from `sql/init-mysql.sql`.

### Current AI service boundary

The Compose image starts `ai-service/server.py`. Spring Boot calls FastAPI `/internal/ai/workflow`, which runs the stateless LangGraph for priority, supply ranking, the supplier-acceptance boundary, allocation, route, weather, ETA, and safety. The `/internal/ai/decision` path is a compatibility alias to that same graph; it does not implement a second orchestration pipeline. LangGraph returns recommendations only and does not connect to a database. MySQL through Spring Boot is the system's only operational database for users, inventory, requests, agent decisions, routes, missions, payments, and telemetry. After Spring dispatches, its telemetry monitor calls `/internal/ai/monitor`, which enters the monitoring node of the same graph. The separate `ai-service/app/main.py` MongoDB application is not started by Docker or the supported run commands and is outside the active system path.

### Existing seed and demo data

`sql/init-mysql.sql` provides the Bengaluru facility directory, zones, drones, hubs, and no-fly-zone records needed to initialize the application. These are seeded operational fixtures, not live fleet telemetry. `DataInitializer` also creates the development bootstrap accounts `admin@meddrone.in` / `admin123` and `operator@meddrone.in` / `operator123`; change those passwords before shared use. Inventory is intentionally not preloaded. The four `.joblib` files under `ai-service/models` are the shipped weather, ETA, anomaly, and demand model artifacts. Existing databases may still contain older demo inventory or fixed accounts; the migration instructions below identify those cases.

### Baseline status in this workspace

The frontend dev server returned HTTP 200. Docker Compose configuration validates, the Spring Boot image builds, and the MySQL, Redis, FastAPI, and Spring containers reach running/healthy states. A seeded administrator login succeeds, Spring can reach FastAPI, all four ML models report loaded, and a weather sample returns an ML-based flight clearance. STOMP connects both directly to Spring `/ws` and through the Vite `/ws` proxy. The first model check exposed missing XGBoost and a scikit-learn version mismatch; the AI dependency configuration now includes XGBoost and pins scikit-learn to the artifact version.

The Phase 1 migration was applied to the running MySQL database. It retained the existing 28 hospitals, 4 zones, 14 airborne units, and 2 users, and created 6 default pricing rules. The rebuilt Spring service starts against the renamed tables; the admin pricing-rules API returns those rules and rejects unauthenticated requests.

## 5. How to Run Locally (manual services)

### Step 1: Initialize Database in MySQL Workbench
1. Open **MySQL Workbench**.
2. Connect to your local MySQL server (port `3306`).
3. Open [`sql/init-mysql.sql`](file:///d:/medical-drone-management-java/medical-drone-management-main/sql/init-mysql.sql).
4. Click the lightning bolt icon (âš¡ Execute) to create `medical_drone_db`, pending hospital directory records without coordinates, zones, drones, and no-fly zones.

### Step 2: Run Spring Boot Backend in VS Code
1. Open the project folder `d:\medical-drone-management-java\medical-drone-management-main` in **VS Code**.
2. Open `backend-spring/src/main/java/com/medicaldrone/MedicalDroneBackendApplication.java`.
3. Click **"Run"** or **"Debug"** (via the Spring Boot Dashboard or Java Extension Pack in VS Code).
   - Alternatively, execute `run_backend.bat` and select Option `1` for MySQL.
4. Backend will start on `http://localhost:8080`.

**Default Login Credentials:**
- **System Admin**: `admin@meddrone.in` / `admin123` (`ROLE_ADMIN`)
- **Central Hub Operator**: `operator@meddrone.in` / `operator123` (`ROLE_CENTRAL_HUB`)

Spring Boot creates these bootstrap accounts with BCrypt on startup. Existing `ROLE_OPERATOR` users are migrated to `ROLE_CENTRAL_HUB`. Requester and supplier accounts are created through the registration form and must select an approved facility. Admins can locate pending directory records or register a facility in **Hospital Network**. Enter its address, select a Bengaluru geocoding match, confirm or adjust the map pin, and save. Then approve the located facility from its row; only then can it be used for account registration.

### Phase 3: Dynamic map and Phase 4: roles

The Leaflet operations map loads live persisted map data from Spring Boot: hospitals from `GET /api/hospitals?verifiedOnly=true`, zones from `GET /api/zones`, drone positions from `GET /api/drones`, active restricted areas from `GET /api/no-fly-zones`, and central hub locations from `GET /api/hubs`. Active mission routes come from the authenticated `GET /api/missions` response and its route waypoints. The map uses OpenStreetMap tiles by default; set `VITE_MAP_TILE_URL` to use another compatible tile provider.

The `/dashboard` route selects Admin, Requester, Supplier, or Central Hub dashboards based on the signed-in role. Backend method security and hospital ownership checks protect request, mission, payment, inventory, supply-response, and fleet operations. Hospital, zone, drone, hub, and no-fly-zone GET endpoints are readable for map display; hospital and zone membership does not grant access to another facility's private operations.

For a database that already contains the old seeded hospital coordinates, apply [`sql/migrations/phase-2-hospital-dynamic-location.sql`](sql/migrations/phase-2-hospital-dynamic-location.sql) once before starting this backend. It makes hospital coordinates nullable and clears only the known seeded directory rows so Admins can confirm their live locations. Do not rerun it after confirming locations. Admin geocoding is available at `POST /api/medical-centers/geocode` and requires an admin bearer token.

If an older database already contains the legacy SQL-seeded admin row, run `sql/migrations/2026-10-04-remove-demo-inventory-and-fixed-accounts.sql` and restart Spring Boot. The migration removes that exact legacy seed row so the initializer can create the working bootstrap account.

### Facility, supplier, and request workflow

1. Apply the Phase 2 location migration if upgrading an existing database, then restart Spring Boot. Locate pending hospitals in **Hospitals**, confirm each map pin, and approve facilities after location is set.
2. On the login page choose **Register**, select an approved Bengaluru facility, and choose **Manage and supply inventory** to create its supplier account.
3. Sign in with that supplier account. Use **Stock Inventory** / **Add inventory item** and record the facility's actual physical quantity. Blood, vaccine, antivenom, and emergency medicine stock requires an expiry date. No sample inventory is preloaded.
4. Requesting accounts can create a request from the dashboard or **Create Request**. Select the material and quantity. Selecting a listed supplier is optional: leave it unassigned to publish an open request to facilities that later record enough eligible stock.
5. System administrators can create requests too; the request form asks which approved facility is requesting. The admin does not act as the medical facility.

For existing MySQL databases, run `sql/migrations/2026-10-04-open-supplier-requests.sql` once in Workbench before submitting an open request. It makes the unknown supplier pickup coordinates nullable until a supplier accepts and provides its actual location.

### Phase 5: supplier matching and rematching

Creating a medical request makes Spring gather eligible stock data and call the LangGraph Priority and Supply Matching nodes. Eligible supplier facilities must be approved, active, geolocated, and have enough unreserved, unexpired stock. The graph ranks them; Spring stores the decision trail in MySQL and creates one active invitation plus queued candidates. Only the invited supplier sees the request in its inbox and receives a notification. A rejection, or an acceptance that can no longer reserve the requested stock, advances the invitation to the next ranked supplier. On acceptance, inventory is reserved and the request moves to `SUPPLIER_ACCEPTED`; the other invitations are closed. If candidates run out, the request becomes `NO_SUPPLIER_AVAILABLE`. An authorized requester, admin, or central hub can retry matching with `POST /api/medical-requests/{id}/rematch` (also available under `/api/requests/{id}/rematch`) after new stock is recorded.

### Phase 6: Razorpay checkout and dispatch gate

Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` in the private project-root `.env` before creating payment orders. Use Razorpay Test Mode credentials for development. The backend calculates the price and creates the order; the frontend opens Razorpay Checkout and sends the returned order, payment, and signature values to `POST /api/payments/verify`. Spring verifies the signature and fetches the payment from Razorpay, and only marks it successful when Razorpay reports a captured payment with the expected order, amount, and currency. A signed webhook at `POST /api/payments/webhook` handles `payment.captured` and `payment.failed` to reconcile asynchronous updates. Configure those two events in the Razorpay dashboard and set the webhook URL to the publicly reachable backend URL plus `/api/payments/webhook`. Keep the webhook secret private and set Razorpay to auto-capture payments. A successful payment moves the request to `READY_FOR_DISPATCH`; hub review and final dispatch are separate steps documented in Phase 7. When credentials are absent, order creation is disabled with a service-unavailable response; there is no mock-payment fallback.

Payment integration follows Razorpay's server-created order and server-side signature-verification flow ([Razorpay integration guide](https://razorpay.com/docs/server-integration/python/test-app/)); webhook events and auto-capture are configured in the [Razorpay dashboard](https://razorpay.com/docs/payments/payments/dashboard/?preferred-country=IN).

### Phase 8: single LangGraph AI orchestration

Spring calls the LangGraph-backed `POST /internal/ai/workflow` for both request supplier ranking and post-acceptance flight recommendations. Supplier matching runs Priority → Supply Matching → `WAIT_FOR_SUPPLIER`; Spring then records the invitation and waits for the supplier. After acceptance and verified payment, Spring sends a fresh state snapshot and the accepted supplier to the graph, which runs Priority → Supply Matching → Allocation → Route → Weather → ETA → Safety and returns `AWAITING_CENTRAL_HUB_REVIEW` when safety passes. The human decision remains in Spring and no graph node writes MySQL state, reserves stock, or dispatches a drone. After operator dispatch, telemetry checks call `/internal/ai/monitor`, routed through the monitoring node in the same LangGraph. `/internal/ai/decision` remains a compatibility alias into the same workflow. `app/main.py` is a separate legacy MongoDB API and is not in this integration path.

### Phase 9: airspace route search and ETA persistence

The Route Agent checks the direct candidate against every active circular no-fly zone using segment-to-circle distance and a safety buffer. If any segment is blocked, it runs an eight-direction grid A* search around the zones; a request with a blocked endpoint or no safe path is rejected before weather, ETA, or Central Hub review. The persisted route records `baseline_eta_minutes`, `ml_eta_minutes`, and `final_eta_minutes` separately. `eta_minutes` and the mission ETA hold the final blended estimate; the `eta_predictions` row stores the model estimate and a snapshot of all three ETA values. The route is an airspace plan and does not use road-network directions.

### Phase 10: existing ML artifacts and datasets

The four supplied joblib artifacts are loaded from `ai-service/models` and used for weather clearance, demand forecasting, ETA prediction, and mission anomaly detection. The weather and medical demand training scripts read the supplied files in `ai-service/data` (`bengaluru_medical_drone_weather_dataset.csv` and `medical_demand_forecasting_dataset.csv`); they do not fetch replacement datasets. `/internal/ai/health` reports each artifact's load state and returns `DEGRADED` if a model cannot load, instead of reporting healthy service status with missing models. Install the root `requirements.txt` in the AI service environment so XGBoost and the pinned scikit-learn version are available to deserialize the shipped pipelines.

### Phase 11: scheduled demand forecasts and inventory-risk alerts

Spring runs a forecast every six hours by default (`DEMAND_FORECAST_CRON` can change the schedule). For each approved, active hospital with a zone and each active medical item, it builds the model inputs from the previous day's and previous seven days' MySQL medical requests, including high/critical request counts. The forecast is stored in `demand_predictions` with hospital and zone; a current `inventory_risk_alerts` row compares next-24-hour predicted demand with available stock and records reserved stock for context. Available stock already excludes reserved units. HIGH and MEDIUM alerts appear in the Admin Dashboard and create `/api/notifications` messages on new risk, escalation, or material shortage increase. Alerts resolve automatically when available stock covers forecast demand. Admins can run an on-demand refresh with `POST /api/ai/demand-forecasts/run` and list current alerts with `GET /api/ai/demand-risk-alerts`. Existing databases must apply `sql/migrations/2026-10-05-demand-forecast-risk-alerts.sql` once; fresh databases receive the tables from `sql/init-mysql.sql`.

### Phase 13: anomaly response and mission operations

The Central Hub anomaly feed includes the affected mission and a rule-based response suggestion from the detected severity/type. Operators and Admins can call `POST /api/missions/{id}/reassign`, `/delay`, or `/abort` with a required `{"reason":"..."}` body. Reassignment marks the affected drone for maintenance, asks the AI workflow for a replacement allocation and route, and returns the mission to `CENTRAL_HUB_REVIEW`; the replacement does not launch until a human approves dispatch. Delay pauses the current telemetry simulation and leaves the mission available for later review/dispatch. Abort stops the simulation and marks the aircraft for maintenance. These endpoints require `ADMIN` or `CENTRAL_HUB`.

Mission monitoring records status transitions and emits a `TIMEOUT` anomaly when a mission exceeds 1.5 times its planned ETA by default. The interval defaults to one minute and can be changed with `MISSION_MONITORING_INTERVAL_MS`; change the threshold with `MISSION_MONITORING_TIMEOUT_MULTIPLIER`.

### Phase 14: audit history

`AuditService` records request creation, supplier accept/reject and stock reservation, hospital approval, AI safety rejection, drone allocation, route approval, payment success, mission dispatch/reassignment/delay/abort, status transitions, and delivery. Authenticated events include `actor_user_id` and `actor_email`; automated monitor and payment gateway events use a system actor label. Admins can review the latest events in the Admin Dashboard or `GET /api/audit-logs?limit=200` (Admin-only, limit capped at 500). Fresh MySQL/PostgreSQL schemas include the actor and creation-time fields. Existing MySQL databases must run `sql/migrations/2026-10-05-audit-actor-and-created-at.sql` once before starting the updated Spring Boot backend; the migration preserves old audit rows and timestamps.

### Phase 7: Central Hub human review

After successful payment, an Admin or Central Hub operator prepares the AI recommendation using `POST /api/medical-requests/{id}/review`. The backend stores the proposed allocation, route, waypoints, ETA, and AI decision trail, reserves the recommended drone as `RESERVED_FOR_REVIEW`, and sets request and mission status to `CENTRAL_HUB_REVIEW`. Preparing this recommendation does not start telemetry or mark the drone as airborne.

The Central Hub dashboard shows the selected drone and its battery, route on the radar map, distance, ETA, and agent decisions including weather and safety. The operator submits `POST /api/missions/{missionId}/hub-decision` with `{"action":"DISPATCH"}`, `{"action":"DELAY","reason":"..."}`, `{"action":"REJECT","reason":"..."}`, or `{"action":"REALLOCATE","reason":"..."}`. `DISPATCH` rechecks payment, request state, route, and drone reservation before assigning the drone and starting telemetry. `DELAY` keeps the drone reserved and permits later dispatch. `REJECT` records the rejected recommendation, releases the drone, and returns the paid request to `READY_FOR_DISPATCH` for a fresh AI review. `REALLOCATE` asks the AI to choose a different available drone and replaces the review route while preserving the decision history. Every hub action is restricted to `ADMIN` or `CENTRAL_HUB` and recorded in the agent decision trail. Existing MySQL installations receive the new decision note column through Hibernate schema update; operators managing DDL manually can apply `sql/migrations/2026-10-05-central-hub-human-review.sql`.

### Step 3: Run Python FastAPI AI Service
```bash
cd ai-service
# Or double-click run_ai_service.bat
python server.py
# AI service is live on http://localhost:8000 (Health check: http://localhost:8000/health)
```

### Step 4: Run React Frontend
```bash
cd frontend
npm install
npm run dev
# Frontend is live on http://localhost:5173
```

---

## End-to-End Mission Dispatch Flow

Hospital request → supplier match and acceptance → inventory reservation → Razorpay payment captured
       ↓
Request status READY_FOR_DISPATCH
       ↓ POST /api/medical-requests/{id}/review
Priority / Weather / Allocation / Route / ETA / Safety recommendations prepared
       ↓
Central Hub reviews the agent trail, selected drone, battery, route map, and ETA
       ├── DELAY → retain review reservation; dispatch later
       ├── REJECT → release drone; return request to READY_FOR_DISPATCH
       ├── REALLOCATE → choose a different drone and refresh the review package
       └── DISPATCH → recheck payment and reservation → set DISPATCHED → start telemetry
