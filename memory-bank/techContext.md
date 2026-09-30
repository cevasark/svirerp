# Technical Context: SVIR ERP

## Technology Stack

### Backend
| Component | Technology | Version | Purpose |
|---|---|---|---|
| Language | Java | 25 (Java SE 25) | Core application platform (verified with JDK 26) |
| Framework | Spring Boot | 3.5.16 | Core framework, DI, auto-configuration |
| Web | Spring Web | 3.5.16 | REST API controllers, embedded Tomcat |
| Persistence | Spring Data JPA / Hibernate | 6.x | Object-relational mapping, repositories |
| Database | MariaDB via MySQL JDBC driver | 11.8 (production and integration-test target) | Relational persistence |
| Migrations | Flyway | 11.7.2 runtime (`flyway-core`, `flyway-mysql`) | Versioned schema migration management |
| Security | Spring Security / OAuth2 Client | 3.5.16 | Session auth, Google OIDC, break-glass login |
| Validation | Jakarta Bean Validation | Hibernate Validator | `@Valid`, `@NotNull`, `@Size` |
| Utilities | Lombok | 1.18.x | Boilerplate generation (`@Getter`, `@Builder`) |
| CSV Parsing | Apache Commons CSV | 1.12.0 | RFC 4180 CSV import/export |
| Payment SDK | Stripe Java | 33.2.0 | Stripe webhook verification and price mapping |
| Email | Jakarta Mail / Spring Mail | 3.5.16 | In-memory RFC 822 MIME assembly for Gmail API |
| Testing | JUnit 5, Mockito, AssertJ | 3.5.16 starter | Unit and integration test suite |

### Frontend
| Component | Technology | Version | Purpose |
|---|---|---|---|
| Framework | Angular | 21.0.0 | Standalone component UI framework |
| Components | Angular Material & CDK | 21.0.0 | UI components (tables, dialogs, form fields) |
| Language | TypeScript | 5.9.x | Typed application logic |
| Reactivity | RxJS | 7.8.0 | Observables for HTTP and streams |
| Change Detection | Zone.js | 0.15.0 | Event coalescing change detection |
| Toolchain | Angular CLI / Vite | 21.0.0 | Compilation and bundling (`@angular/build`) |
| Testing | Karma, Jasmine | 6.4.0 / 5.1.0 | Unit test runner and assertions |

## Local Development Setup & Tooling

### 1. Prerequisites on Windows Host
- **JDK 25 / 26:** Installed in the system (or configured via `JAVA_HOME`).
- **Apache Maven:** Installed in the system.
  - *Note on Wrapper (`mvnw.cmd`):* The checked-in `mvnw.cmd` has a batch variable expansion issue inside parenthesized blocks in Windows cmd; use system Maven or invoke with pre-set variables.
- **Node.js & npm:** Node v24.19.0, npm 11.17.0.
  - In PowerShell, run `npm.cmd` rather than `npm` to avoid unsigned execution policy issues.
- **MariaDB Server:** Production and payment-total integration tests use MariaDB 11.8. Typical local application port is `3306`; isolated tests can use another port.

### 2. Configuration Files
- `src/main/resources/application.properties`: Base default configuration with environment variable overrides.
- `src/main/resources/application-local.properties`: Local developer configuration (gitignored). Contains datasource credentials, break-glass admin bcrypt hash, and AES encryption keys for app settings.
- `deploy.env.example`: Template for environment-based production deployments.

### 3. Build & Test Commands
```powershell
# Backend compilation check (from repository root)
mvn.cmd test-compile

# Run backend tests; payment-total database tests require the opt-in URL below
mvn.cmd test

# Focused service tests for numeric sort routing and the read-only API field
mvn.cmd '-Dtest=MembershipServiceTotalPaidTest' test

# Build the Angular assets, then package them with the backend
npm.cmd --prefix ui run build
mvn.cmd clean package

# Run UI standalone development server (proxies to :8080)
npm.cmd --prefix ui start
```

Maven copies `ui/dist/svirerp-ui/browser` into the JAR; it does not run npm. Build the UI before packaging to include current frontend changes.

### 4. Payment-Total Database Tests

`MemberTotalPaidRepositoryTest` contains nine real-database tests covering source deduplication, attribution, refunds/disputes, zero values, filters, and numeric ordering before pagination. `MemberTotalPaidCollationMigrationTest` adds a regression for migration from legacy `utf8mb4_general_ci` columns to a session using `utf8mb4_uca1400_ai_ci`, then replays V63. It creates and removes its own temporary schema.

Both classes are opt-in and require a disposable local MariaDB 11.8 instance, with root access and an empty password. The URL guard permits `localhost` / `127.0.0.1` and a schema named `svirerp_total_paid_test` (optionally suffixed). Never point these tests at application data. Create the empty test database first; the repository tests apply migrations to it and roll back test fixtures, leaving the schema in place. From the repository root, after starting the isolated database:

```powershell
$env:TOTAL_PAID_TEST_URL = 'jdbc:mysql://127.0.0.1:3307/svirerp_total_paid_test'
mvn.cmd test
Remove-Item Env:TOTAL_PAID_TEST_URL
```

Without this variable the ten database tests are skipped; the three `MembershipServiceTotalPaidTest` cases run normally. The database tests execute the actual Flyway migrations and Hibernate mappings, rather than substituting an in-memory SQL dialect. A MySQL 8.4 trial exposed earlier V40/V42/V44 `MODIFY COLUMN CHECK` compatibility differences; it does not establish equivalent MySQL validation.

### 5. Latest Recorded Verification and Migration Recovery

- **2026-09-27:** `mvn package` passed all 126 tests with zero failures, errors, or skips against disposable MariaDB 11.8, including the V63 mixed-collation regression; the executable JAR was rebuilt.
- **2026-09-29:** After enabling the UI sort control, the three focused `MembershipServiceTotalPaidTest` cases and Angular production build passed. Existing bundle warnings remained: initial bundle 777.12 kB against 500 kB and project-detail styles 2.78 kB against 2 kB. The full backend suite and JAR packaging were not rerun for that UI change.
- V63 now explicitly aligns text collations and uses replaceable views. Recovery for an already failed V63 is documented in [README](../README.md#membership-payment-totals): rebuild/deploy the corrected migration, remove only the failed V63 history row, and restart. Do not remove a successful migration entry; checksum repair alone does not install the corrected views in an environment where V63 already succeeded. Production recovery/deployment has not been verified in this work.
- The runtime Flyway version above is verified from the test classpath. The separate Maven plugin still declares a `flyway-mysql` dependency at 10.10.0 in `pom.xml`; do not confuse that declaration with the application runtime.

## Key Configuration Properties
```properties
# Database connection
spring.datasource.url=jdbc:mysql://localhost:3306/svirerp?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=svirerp
spring.datasource.password=secret

# Hibernate / Flyway
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Local Admin Break-Glass
app.auth.admin.enabled=true
app.auth.admin.username=admin
app.auth.admin.password-hash=$2a$10$...

# Symmetric Setting Encryption Keys (AES-GCM)
app.settings.encryption-key-base64=<32-byte-base64-key>
app.settings.encryption-salt-base64=<16-byte-base64-salt>
```
