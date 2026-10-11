# Mobile Repair Stock System — Java Backend

Spring Boot backend for the Mobile Repair Stock System (migration **Option A**:
keep the existing web UI, move logic/auth to Java, keep Firebase/Firestore).

## Stack

- Java 21 (builds on newer JDKs too)
- Spring Boot 3.5.x, Spring Security
- Firebase Admin SDK (Auth + Firestore) — server side only

## Prerequisites

- JDK 21+ (a full JDK, **not** a JRE — the compiler is required)
- No global Maven required — use the bundled wrapper `./mvnw`

> This machine ships a JRE only at `/usr/lib/jvm/java-25-openjdk-amd64`, so
> `./mvnw` needs a real JDK, e.g.
> `JAVA_HOME=/path/to/jdk-21 ./mvnw package`.

## Run (offline / no Firebase)

Firebase is **off by default**, so the app starts and serves the UI and a small
in-memory inventory API:

```bash
./mvnw spring-boot:run
# open http://localhost:8080
```

`GET /api/health` → `{"status":"ok","firebaseAuth":false}`

## Run (with Firebase)

1. Download a service-account key from the Firebase console
   (Project settings → Service accounts → Generate new private key).
2. Provide credentials and enable Firebase:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
./mvnw spring-boot:run -Dspring-boot.run.arguments=--app.firebase.enabled=true
```

Then `/api/health` reports `"firebaseAuth": true`, the inventory repository
switches to Firestore, and `/api/**` requires a session cookie.

### Auth flow

1. Browser signs in with the Firebase Web SDK and gets an `idToken`.
2. `POST /api/auth/session` `{ "idToken": "..." }`
   → server verifies it and returns an `HttpOnly` `session` cookie (Admin SDK).
- `DELETE /api/auth/session` clears it.

## API (representative)

```
GET    /api/health
POST   /api/auth/session                 DELETE /api/auth/session

# Firestore-shaped document API used by the web UI (app.js auto-detects it)
GET    /api/db/{collection}              # list documents
GET    /api/db/{collection}/{id}         # one document (404 if missing)
PUT    /api/db/{collection}/{id}         # upsert a document
GET    /api/db/{collection}/{id}/stream  # live SSE updates for that document

# Typed inventory API (optional convenience wrappers)
GET    /api/items?q=&category=           POST   /api/items
GET    /api/items/{id}                   PUT    /api/items/{id}
DELETE /api/items/{id}
POST   /api/items/{id}/stock-in          POST   /api/items/{id}/stock-out
GET    /api/orgs/{orgId}/stream          (SSE placeholder)
```

The UI keeps organisations in `organisations`, accounts in `users`, and each
organisation's inventory/transactions/tickets/preferences in the single
`orgData/{orgId}` document — the same shape the browser used against Firestore,
so wiring was a drop-in swap of the data layer.

## How the UI uses the backend

`app.js` probes `/api/health` on load. If it answers, the app switches to
**API mode**: every read/write and the live listener go through the endpoints
above instead of the Firebase SDK or localStorage. The backend-served
`index.html` deliberately omits the Firebase SDK scripts, so this is the path
it takes. The standalone copy in the parent folder still talks to Firebase.

`orgId` on the typed `/api/items` routes comes from the `X-Org-Id` header
(defaults to `demo`); the UI itself uses the `/api/db/**` routes.

## Layout

```
src/main/java/com/mobilerepair/stock/
  Application.java
  config/       FirebaseConfig, SecurityConfig, RepositoryConfig, DocStoreConfig,
                ApiExceptionHandler
  auth/         AuthController, SessionService, FirebaseSessionFilter
  docs/         DocController (Firestore-shaped /api/db/** REST + SSE)
  store/        DocStore, InMemoryDocStore, FirestoreDocStore
  realtime/     DocStreamService, OrgStreamController
  inventory/    ItemController, InventoryService, ItemRepository,
                FirestoreItemRepository, InMemoryItemRepository
  health/       HealthController
  model/        StockItem, StockEvent, Transaction, RepairTicket,
                Organisation, UserProfile, Preferences
src/main/resources/static/   web UI served by the backend (API mode)
```

## Not yet migrated (see JAVA-MIGRATION-PLAN.md)

Sales, phone trades, repairs, ledger/transactions, reports + exports, staff
management, and preferences/i18n still run client-side in `app.js`, but they
already persist through `orgData` via the document API, so they work end to end.
Moving that logic server-side is the remaining step.
