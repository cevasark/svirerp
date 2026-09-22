# Setup Guide — From a Blank Machine to a Running App

Step-by-step instructions to get SVIR ERP running locally, starting from a machine with nothing installed. For *what* the app is and *why* it's built this way, see [ARCHITECTURE.md](ARCHITECTURE.md). For the full configuration/API/migration reference and the real production deployment topology (a specific existing Caddy + WSL2 setup, not a generic "deploy anywhere" guide), see [README.md](README.md).

By the end of this guide you'll have: the database created and migrated, the Spring Boot API running on `:8080`, the Angular UI running on `:4200`, a working login, and the app's one required organization row created — a brand-new database has none, and the app can't do much without it (see [Step 8](#step-8-create-the-first-organization-there-is-no-seed-data)).

---

## Step 1 — Install prerequisites

| Tool | Version | Why |
|---|---|---|
| **Git** | any recent | Clone the repo |
| **JDK** | **25**, exactly — not "any recent JDK" (`pom.xml` pins `<java.version>25</java.version>`) | Compiles/runs the backend |
| **MySQL** | 8.0+ (MariaDB 10.6+/11.x also works — this project is developed against MariaDB in practice) | Application database |
| **Node.js** | 20 LTS or newer | Only needed to build/run the Angular UI |
| **curl** | any | Used in [Step 8](#step-8-create-the-first-organization-there-is-no-seed-data) to bootstrap the app via its API — ships with Windows 10+, macOS, and virtually every Linux distro, so this shouldn't need a separate install |

Maven itself is **not** required — the repo includes the Maven Wrapper (`mvnw` / `mvnw.cmd`), which downloads the pinned Maven version on first use.

**JDK 25**: the most reliable path on any OS is a direct download from [Adoptium (Eclipse Temurin)](https://adoptium.net/temurin/releases/?version=25) — pick your OS/architecture, install, and confirm with `java -version`. Package-manager shortcuts exist too (e.g. `winget install EclipseAdoptium.Temurin.25.JDK` on Windows, `brew install --cask temurin@25` on macOS, or `sdk install java 25-tem` via [SDKMAN!](https://sdkman.io/) on Linux/macOS) — check the manager's own search if an exact package name has drifted since this was written. If you already have an IDE installed that bundles its own JDK 25 (e.g. a recent IntelliJ IDEA ships one under its own install directory), that works too — just point `JAVA_HOME` at it.

**MySQL/MariaDB**: install via your OS's normal path (the official MySQL installer, `apt install mysql-server`/`mariadb-server`, `brew install mysql`, etc.) and make sure the server is **running** before Step 3 — nothing else in this guide starts it for you.

**Node.js**: install via [nodejs.org](https://nodejs.org/) (LTS build) or a version manager (`nvm`, `fnm`, etc.).

---

## Step 2 — Clone the repository

```bash
git clone <this-repo-url> svirerp
cd svirerp
```

> **Windows CMD/PowerShell users:** every `./mvnw` command below becomes `mvnw.cmd`. Everything else (curl, npm, mysql) is identical.

---

## Step 3 — Create the database

```bash
mysql -u root -p -e "CREATE DATABASE svirerp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Using a dedicated app user instead of `root` day-to-day is recommended:

```sql
CREATE USER 'svirerp'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON svirerp.* TO 'svirerp'@'localhost';
FLUSH PRIVILEGES;
```

---

## Step 4 — Configure the backend for local development

```bash
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
```

Edit `src/main/resources/application-local.properties` and fill in `spring.datasource.username`/`spring.datasource.password` with whatever you created in Step 3. Leave the `app.auth.admin.*` and `app.settings.encryption-*` lines alone for now — those are handled in the next two steps. This file is gitignored — it never gets committed, same as production secrets.

---

## Step 5 — Build once, to populate your local Maven cache

```bash
./mvnw -q compile
```

This downloads every dependency into `~/.m2` (only needs to happen once) and confirms the JDK/toolchain is wired correctly before you go further. Step 6 needs one of the jars this downloads.

---

## Step 6 — Generate a local-admin login

There's no seed user in this app — a fresh database has **zero** accounts, and Google sign-in itself can only be configured *from* the admin Settings page, which requires being logged in already. The break-glass local-admin login is the only way in on a brand-new install, so it has to be set up first, even if you intend to use Google sign-in day-to-day afterward.

The password is stored as a bcrypt hash, never plaintext. Generate one with `jshell` against the Spring Security jar Step 5 already downloaded — no throwaway Java file needed:

```bash
CRYPTO_JAR=$(find ~/.m2 -path "*org/springframework/security/spring-security-crypto*/spring-security-crypto-*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)
LOGGING_JAR=$(find ~/.m2 -path "*commons-logging/commons-logging*/commons-logging-*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -1)

echo 'System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("choose-a-strong-password"));' \
  | jshell --class-path "$CRYPTO_JAR:$LOGGING_JAR" -q -
```

This prints a hash like `$2a$10$....` — copy it. (`commons-logging` has to be on the classpath alongside the crypto jar, or `jshell` fails with `NoClassDefFoundError: org/apache/commons/logging/LogFactory`.)

Add both lines to `application-local.properties`:

```properties
app.auth.admin.username = your-chosen-username
app.auth.admin.password-hash = $2a$10$.... (the hash from above, not the plaintext password)
```

While you're editing that file, also set the two encryption values — required before anything ever gets stored as a `SECRET`-type setting (e.g. a Google OAuth client secret, later), and **not safely changeable after the fact** (losing either one permanently strands anything already encrypted with it):

```properties
app.settings.encryption-key = any-long-random-string
app.settings.encryption-salt = 32-hex-characters-e.g-deadbeef00112233...
```

The salt specifically must be a **hex-encoded** string (Spring decodes it as hex, not plain text) — e.g. generate 16 random bytes and hex-encode them (`openssl rand -hex 16` works fine).

---

## Step 7 — Run the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway applies every migration under `src/main/resources/db/migration/` automatically on startup — no separate migrate step needed on a fresh database. Wait for a `Started SvirErpApplication` log line, then confirm the API is answering:

```bash
curl -i http://localhost:8080/api/auth/me
# expect: HTTP/1.1 401 — correct, nobody's logged in yet
```

---

## Step 8 — Create the first organization (there is no seed data)

This app is single-organization-per-install, and the frontend has **no UI to create the first one** — the Settings → Organization page only *edits* an org that already exists. On a genuinely blank database, every org-scoped page in the UI fails outright with "No organization exists yet — create one first." until this step is done, so it has to happen via a direct API call before the UI is usable at all.

```bash
BASE=http://localhost:8080
JAR=/tmp/svirerp-setup-cookies.txt

# 1. GET first to receive the initial CSRF cookie
curl -s -c "$JAR" -b "$JAR" "$BASE/api/auth/me" -o /dev/null

# 2. Log in as the local admin created in Step 6
XSRF=$(grep XSRF-TOKEN "$JAR" | awk '{print $7}')
curl -s -c "$JAR" -b "$JAR" -X POST "$BASE/login/local" \
  -H "X-XSRF-TOKEN: $XSRF" \
  -d "username=your-chosen-username&password=choose-a-strong-password"
# expect: empty body, HTTP 204

# 3. GET again — POST /login/local clears the XSRF-TOKEN cookie, so a fresh
#    one has to be reissued before the next mutating call will be accepted
curl -s -c "$JAR" -b "$JAR" "$BASE/api/auth/me" -o /dev/null

# 4. Create the organization (only `name` is required)
XSRF=$(grep XSRF-TOKEN "$JAR" | awk '{print $7}')
curl -s -b "$JAR" -X POST "$BASE/api/organizations" \
  -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -d '{"name":"Your Organization Name"}'
# expect: HTTP 201 with the created organization JSON
```

The rest of the organization's fields (legal name, address, tax ID, …) can be filled in afterward through the UI's Settings → Organization tab — `name` is the only one needed to unblock everything else.

---

## Step 9 — Install and run the frontend

```bash
cd ui
npm install
npm start
```

This starts the Angular dev server at `http://localhost:4200`. It proxies `/api`, `/oauth2`, `/login`, and `/logout` to the backend on `:8080` (`ui/proxy.conf.json`), so the browser sees everything as same-origin — required for the session cookie to work, and it means the backend from Step 7 must already be running.

---

## Step 10 — Log in

Open `http://localhost:4200/portal-access` (this route is intentionally not linked from any nav — it's the break-glass form) and sign in with the username/password from Step 6. You should land on the dashboard, now that Step 8 has given the app an organization to be scoped to.

---

## Step 11 (optional) — Set up Google sign-in

Once logged in as the local admin, Google sign-in can be configured from the UI itself — no more property-file edits needed for this part:

1. In Google Cloud Console: set the OAuth consent screen's **User Type = Internal** (restricts sign-in to your Workspace org at Google's level), then create an OAuth 2.0 Client ID (Web application) with authorized redirect URI `http://localhost:8080/login/oauth2/code/google` for local dev.
2. In the app, go to **Settings** (`/settings`) and enter that client's ID/secret. It's encrypted at rest using the key/salt from Step 6 and takes effect on the very next login attempt — no restart.

Full details, including the separate production client and the org's Workspace-domain restriction, are in [README.md's Authentication section](README.md#authentication).

---

## Step 12 (optional) — Build a single production-style jar

To run the app the way production does — one jar serving both the API and the built UI from a single origin:

```bash
cd ui && npm run build && cd ..
./mvnw clean package -DskipTests
java -jar target/svirerp-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
# → http://localhost:8080 now serves both the UI and /api/**
```

This repo's actual production deployment (a specific Caddy reverse proxy + WSL2 topology, plus the `redeploy.sh` script that automates this exact stop/rebuild/restart sequence there) is documented in [README.md's Production Access section](README.md#production-access) — that part is environment-specific to how this app is already hosted, not a generic deployment guide.

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| `UnsupportedClassVersionError` / build fails immediately | The wrong JDK is on `PATH`/`JAVA_HOME` — confirm `java -version` reports 25, not whatever your OS shipped with |
| Port `8080` already in use | Something else is already listening there — set `--server.port=NNNN` on the run command, or stop the other process |
| `jshell` fails with `NoClassDefFoundError: org/apache/commons/logging/LogFactory` | The `commons-logging` jar was left off the `--class-path` in Step 6 — both jars are required together |
| Every page after login says *"No organization exists yet"* | Step 8 hasn't been done (or failed) — the app genuinely cannot function without at least one organization row |
| Browser console shows `"Invalid CORS request"` on a login/save action | `app.cors.allowed-origins` doesn't include the origin the browser is actually calling from — for the dev server that's `http://localhost:4200` (the default), for a custom setup add it to `application-local.properties` |
| Flyway fails with `Validation failed... checksum mismatch` | A migration file under `db/migration/` was edited after already being applied to this database — migrations are treated as immutable once applied; fix forward with a new migration instead of editing history |
| Google sign-in redirects to a `redirect_uri_mismatch` error | The redirect URI registered in Google Cloud Console doesn't exactly match what the app sent — for local dev it must be `http://localhost:8080/login/oauth2/code/google` (port 8080, the **backend's** port, not 4200) |
