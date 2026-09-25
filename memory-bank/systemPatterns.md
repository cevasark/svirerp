# System Patterns: SVIR ERP

## Architecture Overview
The system follows a cohesive single-origin architecture:
- **Backend:** Java 25, Spring Boot 3.5.16 REST API with Spring Data JPA and Hibernate 6.
- **Frontend:** Angular 21 Single Page Application using Standalone Components, Angular Material, and Angular Signals.
- **Packaging:** Maven `copy-resources` embeds the built Angular distribution (`ui/dist/svirerp-ui/browser`) into Spring Boot's `target/classes/static/`, producing a single runnable `.jar`.
- **Origin & Security:** Production operates strictly same-origin. Authentication utilizes standard `HttpSession` cookies with `SameSite=Lax` and cookie-based CSRF protection (`XSRF-TOKEN`).

```
Browser Client (Angular 21 SPA)
       │
       │ HTTP /api/** (same origin, session cookie, XSRF)
       ▼
Spring Boot 3.5.16 Application
 ├── Security Filter Chain (OAuth2 OIDC / Local Admin / Webhook Bypass)
 ├── REST Controllers (@RestController, thin DTO / Entity contracts)
 ├── Application Services (@Service, @Transactional boundaries)
 ├── Spring Data JPA Repositories (@EntityGraph / Fetch Joins)
 └── Database Migration Engine (Flyway 10)
       │
       ▼
 MariaDB 11.8 / MySQL 8.0 Database (Flyway DDL-managed schema)
```

## Backend Architectural Patterns

### 1. Package-by-Domain Structure
Located under `com.svivanrilski.svirerp`:
- `auth`: Google OAuth2 authentication handler, break-glass local admin, security filter configurations.
- `common`: `GlobalExceptionHandler`, base error envelopes, cross-cutting utilities.
- `person`: Core person identities, search, and overview aggregation (`PersonOverviewService`).
- `organization`: Singleton organization entity and settings.
- `membership`: Membership types, member rosters, contribution tracking, `TierCalculator`.
- `finance`: Chart of accounts, funds, journal entries, balanced transaction postings, financial statements.
- `governance`: Trustees, committees, meeting minutes, action items, projects, tasks, checklists.
- `event`: Calendar events, church services, registrations, resources, Google Calendar sync.
- `volunteer`: Volunteers, volunteer areas, assignment mapping, volunteer hour logging.
- `settings`: Central encrypted `app_setting` store with AES-GCM encryption (`SettingEncryptor`).
- `email`: Gmail API HTTPS integration with in-memory MIME assembly.
- `stripeintegration`: Stripe webhook signature verification, price mapping, payment application.
- `zeffyintegration`: Comprehensive 5-phase Zeffy API client, webhook inbox, payment processor, sync coordinator, lifecycle/correction coordinator, and contact synchronizer.

### 2. Database & Persistence Rules
- **Flyway Sole DDL Ownership:** `spring.jpa.hibernate.ddl-auto=validate`. Hibernate never mutates the schema. Every change is an immutable migration (`V1` to `V62`).
- **Open-in-View Disabled:** `spring.jpa.open-in-view=false`. Hibernate sessions terminate when service transactions end. To avoid `LazyInitializationException` during JSON serialization:
  - Repositories declare `@EntityGraph(attributePaths = {...})` for standard queries.
  - Complex nested relationships use explicit JPQL `JOIN FETCH` queries.
- **Defense-in-Depth Constraints:** Domain statuses and types are enforced by MySQL/MariaDB `CHECK` constraints as well as service-layer validation.
- **Auditing Columns:** Domain tables standardly declare `created_at` and `updated_at` defaults.

### 3. Integration & Webhook Ingress Patterns
- **Webhook Ingress Exemption:** Only `/api/webhooks/stripe` and `/api/webhooks/zeffy` bypass session auth and CSRF.
- **Raw Body Signature Verification:**
  - Stripe uses `Stripe-Signature` via official `stripe-java` SDK.
  - Zeffy uses `Zeffy-Signature` (timestamp + HMAC-SHA256 of raw body) verified by `ZeffyWebhookSignatureVerifier`.
- **Durable Inbox Before Processing:** Inbound events are committed to `zeffy_webhook_event` with raw JSON and SHA-256 payload hash before asynchronous or downstream domain writes occur.
- **Idempotency Boundaries:**
  - Webhooks: `zeffy_event_id` unique constraint.
  - Payments: `zeffy_payment_id` unique constraint on `zeffy_payment`.
  - Contacts: `zeffy_contact_id` unique constraint on `zeffy_contact`.
- **Rate-Limiting & Request Pacing:** Outbound calls to Zeffy are throttled through `ZeffyRequestPacer` to 1 request per second to ensure compliance with API limits.

### 4. Financial & Membership Posting Coordination
- **Atomic Processing:** When an inbound payment is applied, Person lookup/creation, Member record creation/extension, `MemberPayment` contribution record, and balanced double-entry `JournalEntry` are committed within a single database transaction.
- **Reversal & Correction Chain:** Refunds, disputes, or amount changes produce linked correction journal entries (`corrects_journal_entry_id`) rather than altering historical journal records.

## Frontend Architectural Patterns

### 1. Angular 21 Standalone Components
- Zero `NgModule` usage across the entire frontend application.
- Feature grouping into directories:
  - `core/`: Global guards (`authGuard`, `adminGuard`), HTTP interceptors (`apiInterceptor`, `errorInterceptor`), shared services (`AuthService`, `ResourceService<T>`), models.
  - `shared/`: Generic UI components (`DataTableComponent`, `PageHeaderComponent`, `AutocompleteComponent`, `ConfirmDialogComponent`).
  - `features/`: Lazy-loaded routed modules (`finance`, `membership`, `persons`, `governance`, `events`, `volunteers`, `settings`).

### 2. State & Reactivity
- UI state is managed with Angular Signals (`signal()`, `computed()`) within components and services.
- No global state library (NgRx) — state is localized to the active page or scoped within domain services.

### 3. Deep Link Preservation
- Unauthenticated access to deep links (e.g. `/governance/projects/:id`) is captured in `sessionStorage` by `authGuard`.
- Upon successful Google OAuth redirect or break-glass login, the user is navigated directly back to their target URL.
