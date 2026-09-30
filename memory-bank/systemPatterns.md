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
 └── Database Migration Engine (Flyway 11.7.2 runtime)
       │
       ▼
 MariaDB 11.8 Database (validated migration target; MySQL JDBC driver)
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
- **Flyway Sole DDL Ownership:** `spring.jpa.hibernate.ddl-auto=validate`. Hibernate never mutates the schema. Schema changes use versioned migrations (`V1` through `V63`); do not rewrite successfully applied migration history. V63 was corrected for a failed mixed-collation migration, with recovery details in [README](../README.md#membership-payment-totals).
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

### 5. Derived Lifetime Payment Totals

- **One Person-Level Aggregate:** `Member.totalPaid` is a read-only `BigDecimal` API field backed by Hibernate `@Formula`, reading `person_payment_total` by `person_id` with a zero fallback. No separate balance is maintained. Every membership for the same person shows the same lifetime total, independent of tier, status, or campaign.
- **V63 View Pipeline:** `zeffy_payment_loss` reconciles successful refund and lost-dispute snapshots with lifecycle rows by external ID, preferring the latest snapshot when an ID is present. `person_payment_amount` combines net successful Zeffy payments, posted revenue journal lines, successful Stripe events without a journal, and standalone completed membership payments. `person_payment_total` groups attributable amounts by person into `DECIMAL(19,2)`.
- **Source Identity Prevents Double Counting:** Integration links and external transaction references exclude copied membership payments and Zeffy accounting/correction entries. Person resolution uses existing person/member/contribution/contact/journal links, then unique external references and unambiguous normalized email. Unattributable payments are excluded. Independent manual records without shared source identity remain distinct.
- **Financial Meaning:** Totals cover all payment purposes in the application's supported USD currency, less recorded refunds/lost disputes and before processing fees. Zeffy campaign APPLY/IGNORE policy does not restrict this report or cause new accounting writes; external deletion is not a refund. Stripe refunds affect totals when recorded in the local ledger because Stripe refund webhooks are not currently ingested.
- **Explicit Collations:** Natural-key, currency, and email comparisons and matching grouping expressions use `utf8mb4_general_ci`, avoiding MariaDB 1267 when legacy columns and `JSON_TABLE` session defaults differ. All three views use `CREATE OR REPLACE VIEW` so a partially applied V63 can be replayed without altering table collations or stored payment data.
- **Sort Before Pagination:** `MembershipService.findAllMembers` passes the `totalPaid` sort to JPA so SQL orders numerically before `LIMIT/OFFSET`. It appends ascending member `id` only when no explicit ID tie-breaker was requested, preserving filters and other requested ordering.

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

### 4. Membership Total Paid Display and Sorting

- The Members table places **Total Paid** immediately after **Expiry Date**, formats it as USD currency, and displays missing totals as zero.
- The column is sortable through the shared table's desktop header and mobile **Sort by** control. `totalPaid,asc` / `totalPaid,desc` travels through the existing member service to the pageable API; the UI does not sort only the loaded page.
- Changing sort resets to page zero and retains the membership status/type filters. Enabling the control reused the existing backend aggregate and required no additional migration.
