# Architecture

High-level design/architecture reference for SVIR ERP — a single-organization non-profit ERP. For setup steps, the full API surface, and the migration-by-migration history, see [README.md](README.md); this document is orientation for someone new to the codebase, not a reference.

**Shape of the system:** a Spring Boot backend and an Angular frontend, built and shipped as **one deployable artifact** — `mvn package` copies the Angular production build into the jar's static resources, so in production Spring Boot serves both the UI and `/api/**` from a single origin. That single-origin choice is deliberate, not incidental: auth is session-cookie based, and same-origin avoids the cross-origin `SameSite`/CORS complications a cookie-based session would otherwise need. Locally the two run separately (Angular dev server on 4200, API on 8080) with a dev proxy standing in for that same-origin relationship.

---

## Backend

**Stack:** Java 25, Spring Boot 3.5.16, Spring Data JPA / Hibernate 6, Spring Security (OAuth2 client + session-based form login), Flyway 10, MySQL 8-compatible (dev/prod both run on MariaDB 11.8).

### Structure: package-by-domain, not package-by-layer

`src/main/java/com/svivanrilski/svirerp/` has one package per business domain — `auth`, `common`, `email`, `event`, `finance`, `governance`, `membership`, `organization`, `person`, `settings`, `stripeintegration`, `volunteer`, `zeffyimport` — and each domain package holds its **own** Entity/Repository/Service/Controller classes together, rather than the app being sliced into top-level `controllers/`, `services/`, `repositories/` folders. A feature living in one place (e.g. everything Governance-related — Trustees, Committees, Meeting Minutes, Projects — sits in `governance/`) was chosen over grouping by technical layer, since most changes touch one domain end-to-end.

Within a domain package, the conventional layering still holds:

```
Controller (@RestController, thin — path mapping, request/response shape only)
    ↓
Service (@Service, @Transactional — business rules, validation, orchestration)
    ↓
Repository (Spring Data JPA interface — query methods, @EntityGraph)
    ↓
Entity (@Entity — JPA-mapped, Lombok @Getter/@Setter/@Builder)
```

A few patterns repeat across every domain, worth knowing once rather than re-discovering per feature:

- **`spring.jpa.open-in-view=false`** — the Hibernate session closes before the controller layer serializes the response. Every lazy association a response body actually walks must be eagerly fetched by the repository query itself (`@EntityGraph(attributePaths = {...})`, or an explicit `JOIN FETCH` `@Query` when the lazy chain gets too deep for the declarative form to resolve reliably), or serialization throws `LazyInitializationException`. This is the single most common gotcha when adding a new nested response shape.
- **Flyway owns all DDL; Hibernate never does** (`spring.jpa.hibernate.ddl-auto=validate`) — every schema change is a versioned, checked-in `V<n>__description.sql` file under `src/main/resources/db/migration/` (51 migrations, 44 tables at last count). Migrations are treated as immutable once applied to a real database — a later migration alters/renames rather than editing history in place.
- **DB `CHECK` constraints double as the first line of defense** for enum-like string columns (status, category, payment method, …), with the same allowed-value set re-validated in the Service layer — belt-and-braces, not redundant, since the DB constraint is the backstop against any write path that skips the service.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`, in `common/`) centralizes error responses into one JSON envelope (`{timestamp, status, error, message}`) across the whole app, so controllers don't `try`/`catch`.

### Authentication model

Session-cookie based, not JWT/stateless. Two ways to sign in, both landing in the same `HttpSession`:

- **Google OAuth2/OIDC**, restricted to the org's Google Workspace hosted domain (checked via the `hd` claim). The OAuth client id/secret are **not** a property file value — they're stored (secret encrypted) in the `app_setting` table and resolved fresh on every login attempt, so they rotate with no app restart.
- **Local admin** — a single break-glass account (bcrypt password hash), rate-limited and audit-logged, for when Google sign-in is unreachable or not yet configured.

Once authenticated via either method, a user has the same full API access — auth here is a pure access gate, not a permission system, with the single exception of `/settings/**` (`ROLE_ADMIN`, which only the local-admin login carries). See the [README's Authentication section](README.md#authentication) for the full setup/rotation details.

### Backend third-party dependencies

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST controllers, embedded Tomcat, Jackson JSON (de)serialization |
| `spring-boot-starter-data-jpa` | Spring Data JPA repositories + Hibernate 6 ORM |
| `spring-boot-starter-validation` | Bean Validation (`@NotNull`/`@NotBlank`/`@Valid`) on request bodies |
| `spring-boot-starter-security` + `spring-boot-starter-oauth2-client` | Session-based auth; Google OAuth2/OIDC login |
| `spring-boot-starter-mail` | `MimeMessage`/`MimeMessageHelper` only — builds RFC 822 messages in memory for the **Gmail API** (not SMTP); delivery is HTTPS to Google, no mail-server connection is ever opened |
| `flyway-core` + `flyway-mysql` | Schema migrations; sole owner of all DDL |
| `mysql-connector-j` | MySQL/MariaDB JDBC driver (runtime scope) |
| `commons-csv` (Apache Commons) | RFC 4180 CSV read/write for the Member import/export template |
| `poi-ooxml` (Apache POI) | Reads `.xlsx` — Zeffy's payment/transaction export is a real Excel file, not CSV |
| `stripe-java` | Stripe webhook signature verification + API calls (e.g. listing Prices for product-mapping) |
| `lombok` | Compile-time boilerplate reduction (`@Getter`/`@Setter`/`@Builder`/…) on entities and DTOs; stripped from the runtime jar |
| `spring-boot-starter-test` + `spring-security-test` | Test scope only |

---

## Frontend (Angular UI)

**Stack:** Angular 21, **standalone components** (no `NgModule`s anywhere in the app), Angular Material + Angular CDK, RxJS 7. Zone.js-based change detection (with event coalescing) — not zoneless, no SSR.

### State: signals, no global store

Component and service state is plain Angular **signals** (`signal()`/`computed()`), not an NgRx-style global store — deliberate, matching the app's scope: most state is either "the list this page is currently showing" (owned by that page's component) or "who's logged in" (`AuthService.currentUser` signal, read app-wide via DI). There's no cross-cutting client-side state that would justify a store's overhead.

### Structure

```
core/        — guards, HTTP interceptors, app-wide services, DI tokens, shared TS models
shared/      — reusable "dumb" components (confirm-dialog, data-table, page-header, autocomplete)
features/<domain>/
  <domain>.routes.ts   — lazy-loaded route group for this feature
  pages/               — routed components (list pages, detail pages, dialog forms)
  services/            — one HTTP service per resource, usually extending ResourceService<T>
```

`ResourceService<T>` (in `core/services/`) is the base class nearly every feature service extends — it supplies `getPage()`/`getById()`/`create()`/`update()`/`remove()` against a fixed resource path, so a new CRUD feature's service is typically a handful of lines plus whatever non-generic endpoints it needs.

### Routing & guards

One top-level route (`''`) wraps `ShellComponent` and carries `authGuard`; every feature route group is a lazy-loaded child of it, so access control lives in exactly one place rather than being repeated per feature. `settings` additionally stacks `adminGuard`. Both guards are plain functions (`CanActivateFn`), not class-based `Injectable` guards.

`authGuard` also doubles as the landing point for **shared deep links**: an unauthenticated visit to any page (e.g. a Project or Meeting Minutes detail link) has its URL stashed in `sessionStorage` before redirecting to `/login`, then restored and navigated to as soon as login completes — `sessionStorage` specifically because Google sign-in is a full-page round trip through accounts.google.com, which nothing in-memory (a JS variable, router state) survives.

### HTTP layer

`HttpClient` with two interceptors (`core/interceptors/`): `apiInterceptor` (sets a common `Accept` header) and `errorInterceptor` (one place that turns every HTTP error into a user-facing toast, redirects to `/login` on an unexpected 401 with the same sessionStorage return-URL handling as the guard, and lets component code re-`catchError` for anything it needs to react to locally, like resetting a loading flag). CSRF is cookie-based (`XSRF-TOKEN` / `X-XSRF-TOKEN`), wired through `withXsrfConfiguration` rather than manual header plumbing.

### Frontend third-party dependencies

| Dependency | Purpose |
|---|---|
| `@angular/core`, `common`, `forms`, `router`, `compiler`, `platform-browser` | Angular framework itself |
| `@angular/animations` | Angular Material's animation driver |
| `@angular/material` + `@angular/cdk` | Component library (dialogs, tables, form fields, menus, tooltips, …) and its underlying primitives |
| `rxjs` | Observables — `HttpClient`, interceptors, and most async component code |
| `zone.js` | Angular's default (non-zoneless) change-detection mechanism |
| `tslib` | TypeScript helper runtime (transitive requirement of the above) |
| `@angular/cli`, `@angular/build`, `@angular-devkit/build-angular`, `@angular/compiler-cli`, `typescript` | Build toolchain (dev-only) |
| `karma`, `karma-chrome-launcher`, `karma-coverage`, `karma-jasmine`, `karma-jasmine-html-reporter`, `jasmine-core`, `@types/jasmine` | Test runner (dev-only; `ng test`) |

No state-management library (NgRx/NGXS), no UI kit beyond Angular Material, no HTTP client beyond Angular's own — kept intentionally small.
