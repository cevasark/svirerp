# Technical Context: SVIR ERP

## Technology Stack

### Backend
| Component | Technology | Version | Purpose |
|---|---|---|---|
| Language | Java | 25 (Java SE 25) | Core application platform (verified with JDK 26) |
| Framework | Spring Boot | 3.5.16 | Core framework, DI, auto-configuration |
| Web | Spring Web | 3.5.16 | REST API controllers, embedded Tomcat |
| Persistence | Spring Data JPA / Hibernate | 6.x | Object-relational mapping, repositories |
| Database | MariaDB / MySQL | 11.8 (prod) / 8.0+ | Relational persistence |
| Migrations | Flyway | 10.x (`flyway-mysql`) | Versioned schema migration management |
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
- **MariaDB / MySQL Server:** Accessible at `localhost:3306`.

### 2. Configuration Files
- `src/main/resources/application.properties`: Base default configuration with environment variable overrides.
- `src/main/resources/application-local.properties`: Local developer configuration (gitignored). Contains datasource credentials, break-glass admin bcrypt hash, and AES encryption keys for app settings.
- `deploy.env.example`: Template for environment-based production deployments.

### 3. Build & Test Commands
```powershell
# Backend compilation check
mvn test-compile

# Run backend unit and integration test suite (113 passing tests)
mvn test

# Package complete single-jar deployment (builds backend and static assets)
mvn clean package -DskipTests

# Run UI standalone development server (proxies to :8080)
cd ui
npm.cmd start

# Build Angular production bundle
cd ui
npm.cmd run build
```

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
