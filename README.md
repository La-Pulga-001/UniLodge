# UniLodge

UniLodge is a student-accommodation platform for Ghana. Students browse approved
listings near their university, compare rent and distance, save favourites,
post roommate requests, comment and contact landlords directly. Landlords
publish listings after administrator approval. Administrators moderate
accounts, listings and posts and manage each other through an audited
governance flow.

The first supported institution is **Palm University College** (Shai Hills,
Greater Accra). Further institutions can be added from the admin workspace
without redeploying.

- Currency: Ghana cedi (GH₵)
- Payments: none. UniLodge only provides direct call / WhatsApp / email contact.
- Backend: Java 25, Spring Boot 3.5.6, PostgreSQL 18
- Frontend: plain HTML, CSS and JavaScript served by Spring Boot (no build step)

---

## 1. Repository layout

```
unilodge-common/       enums, DTO records, shared exceptions and geo helpers
unilodge-persistence/  JPA entities, repositories, specifications, Flyway SQL
unilodge-service/      business logic: auth, OTP, email, storage, audit, admin
unilodge-web/          Spring Boot app, REST controllers, security, static UI
docs/API.md            endpoint reference
Dockerfile             multi-stage production image
docker-compose.yml     PostgreSQL + application stack
.env.example           environment template for Docker Compose
run-dev.bat            local Windows launcher (port 8081)
```

Each module is a normal Maven module. The executable artifact is
`unilodge-web/target/unilodge-web-1.0.0.jar`.

---

## 2. Prerequisites

| Tool | Version |
| --- | --- |
| JDK | 25 or newer (the build targets Java 25) |
| Maven | not required; use the bundled `mvnw` / `mvnw.cmd` |
| PostgreSQL | 14+ (tested on 18) |
| Browser | current Chrome, Edge or Firefox |

No Node.js, npm or frontend build tooling is needed.

---

## 3. Local setup (Windows)

### 3.1 Create the database

Using `psql` as the PostgreSQL superuser:

```sql
CREATE USER unilodge_app WITH PASSWORD 'un1l0dge_local_2026';
CREATE DATABASE unilodge OWNER unilodge_app;
```

The default connection settings live in
`unilodge-web/src/main/resources/application.yml`:

```
jdbc:postgresql://localhost:5432/unilodge   (DB_URL)
unilodge_app                                (DB_USERNAME)
un1l0dge_local_2026                         (DB_PASSWORD)
```

Flyway creates the schema and seeds the initial institution on first start.

### 3.2 Build

```bat
mvnw.cmd -DskipTests package
```

### 3.3 Run

Development profile (console email, OTP codes shown in the UI, demo accounts):

```bat
run-dev.bat
```

or directly:

```bat
java -jar unilodge-web\target\unilodge-web-1.0.0.jar --spring.profiles.active=dev --server.port=8081
```

Open <http://localhost:8081/>.

> Port 8080 may be used by another local web server. `run-dev.bat` defaults to
> **8081**; override with `set PORT=9000` before running it. For a different
> public address also set `APP_URL`, because it is used in OTP and invitation
> links.

### 3.4 Demo accounts (development profile only)

| Role | Email | Password |
| --- | --- | --- |
| Student | `kwame.student@unilodge.test` | `Demo@1234` |
| Approved landlord | `ama.landlord@unilodge.test` | `Demo@1234` |
| Pending landlord | `kofi.landlord@unilodge.test` | `Demo@1234` |

Demo data is controlled by `app.bootstrap.demo-data` and is disabled in
production.

### 3.5 First administrator setup

The three original administrator emails are configured in
`app.bootstrap.admin-emails`:

- `fblesselikem@gmail.com`
- `ayensusamuel866@gmail.com`
- `deiyeshua05@gmail.com`

They are created on first boot **without a password**. To activate one:

1. Open <http://localhost:8081/admin-setup.html>.
2. Enter the administrator email and request a setup code.
3. In development the code is printed in the server log and echoed in the
   response (`devCode`) because `app.mail.expose-otp=true`. In production it is
   emailed.
4. Enter the 6-digit code and choose a password. The account becomes an active,
   verified original administrator.

Only original administrators may invite or promote other administrators.
Invitations create an admin link
(`/admin-setup.html?invite=TOKEN`) that expires after seven days and still
requires email OTP verification. Promoted existing users must verify their
email before gaining moderation powers.

### 3.6 Password reset

`/login.html` → **Forgot password**. Request a code, verify it, then set a new
password. Codes expire after 10 minutes and allow five attempts. Resend is
limited to once every 60 seconds.

---

## 4. Configuration

All settings can be supplied as environment variables. The most important:

| Variable | Purpose | Default |
| --- | --- | --- |
| `PORT` | HTTP port | `8080` |
| `DB_URL` | JDBC URL | local `unilodge` database |
| `DB_USERNAME` / `DB_PASSWORD` | database credentials | local dev values |
| `JWT_SECRET` | token signing secret (32+ chars) | dev-only value |
| `JWT_TTL_HOURS` | session lifetime | `168` |
| `APP_URL` | public URL used in emails and links | `http://localhost:8080` |
| `MAIL_MODE` | `console` or `smtp` | `console` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP delivery | — |
| `UPLOAD_DIR` | photo storage directory | `./uploads` |
| `GEOCODING_ENABLED` | OpenStreetMap/Nominatim lookups | `true` |
| `CORS_ORIGINS` | extra allowed browser origins | localhost only |
| `LOG_FILE` | production log path | `logs/unilodge.log` |

Profiles: `dev` (default) and `prod`. The `prod` profile requires the database,
JWT, mail and `APP_URL` variables listed in `application-prod.yml`.

Uploaded files are stored on disk under `UPLOAD_DIR` and served from
`/api/files/**`. Back up that directory together with the database.

---

## 5. Roles and rules

**Student**
- Browse, search, filter and sort approved listings
- Save listings and posts
- Create, edit and delete their own roommate posts
- Comment on posts and delete their own comments
- Contact landlords (phone, WhatsApp, email) when signed in

**Landlord**
- Everything a student can do, plus create and edit listings
- Upload up to 10 photos per listing (JPEG, PNG or WebP, max 5 MB each)
- Pin an exact location on the Leaflet/OpenStreetMap picker
- Cannot publish until an administrator approves the landlord account
- Every new or edited listing is reset to `PENDING_APPROVAL`

**Administrator**
- View users and statistics
- Approve or reject landlord accounts
- Approve, reject, suspend and restore listings
- Hide or restore posts; hide or remove comments
- Suspend and reactivate accounts
- Add and update institutions
- View the audit trail
- Original administrators only: invite administrators, promote existing users,
  revoke administrator access

Safety rules:
- Administrator accounts cannot be suspended.
- The last active administrator and the last original administrator cannot be
  removed.
- Self-revocation is blocked.
- Revoking an admin returns them to `LANDLORD` if they own listings, otherwise
  `STUDENT`.
- Every moderation and governance action is written to the audit log.

Statuses:
- Users: `PENDING_APPROVAL`, `ACTIVE`, `REJECTED`, `SUSPENDED`
  (students are `ACTIVE` immediately; landlords begin as `PENDING_APPROVAL`)
- Listings: `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`, `REMOVED`
- Posts/comments: `VISIBLE`, `HIDDEN`, `REMOVED`

---

## 6. API

Interactive documentation is available while the app runs:

- Swagger UI: <http://localhost:8081/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8081/v3/api-docs>
- Full endpoint reference with request/response examples: [docs/API.md](docs/API.md)

Authentication uses `Authorization: Bearer <token>`. Public catalogue and
institution reads are anonymous; landlord contact details are only returned to
signed-in users.

---

## 7. Database

The schema is managed by Flyway in
`unilodge-persistence/src/main/resources/db/migration`:

- `V1__init.sql` — tables, constraints and indexes
- `V2__seed_university.sql` — Palm University College

Main tables: `users`, `universities`, `properties`, `property_images`,
`property_amenities`, `saved_properties`, `student_posts`, `comments`,
`saved_student_posts`, `otp_tokens`, `admin_invitations`, `admin_audit_logs`.

Hibernate runs in `validate` mode, so the application fails fast if the schema
and entities drift. To start from scratch locally:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

then restart the application; Flyway reapplies the migrations.

---

## 8. Production deployment

### 8.1 Docker Compose (recommended)

1. Copy `.env.example` to `.env` and fill in every value.
2. Build and start:

```bash
docker compose up -d --build
```

3. Check health and logs:

```bash
docker compose ps
docker compose logs -f app
```

The app is exposed on port 8080. Put a TLS-terminating reverse proxy (Caddy,
Nginx, a cloud load balancer) in front of it and set `APP_URL` to the public
HTTPS URL. Persist the `unilodge-db`, `unilodge-uploads` and `unilodge-logs`
volumes.

### 8.2 Plain server

1. Install JDK 25 and PostgreSQL.
2. Create the database and user.
3. Build on a workstation or CI: `mvnw.cmd -DskipTests package`.
4. Copy `unilodge-web/target/unilodge-web-1.0.0.jar` to the server.
5. Export the environment variables from section 4 (`SPRING_PROFILES_ACTIVE=prod`).
6. Run as a service, for example:

```ini
# /etc/systemd/system/unilodge.service
[Unit]
Description=UniLodge
After=network.target postgresql.service

[Service]
User=unilodge
EnvironmentFile=/etc/unilodge.env
ExecStart=/usr/bin/java -jar /opt/unilodge/unilodge-web-1.0.0.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

Back up nightly:

```bash
pg_dump -U unilodge_app unilodge > unilodge-$(date +%F).sql
tar czf uploads-$(date +%F).tar.gz /path/to/uploads
```

After deployment, open `/admin-setup.html` once for each original
administrator. Use a unique password and complete the email OTP step.

### 8.3 First-run checklist

- [ ] `JWT_SECRET` is long and random
- [ ] Database password is not the development password
- [ ] `APP_URL` is the public HTTPS address
- [ ] SMTP credentials are valid and `MAIL_FROM` is a real sender
- [ ] `app.mail.expose-otp` is `false` (default in `prod`)
- [ ] Demo data is disabled
- [ ] Uploads and logs directories are writable and persisted
- [ ] All three original admins completed setup
- [ ] Backups are scheduled

---

## 9. Useful commands

```bat
mvnw.cmd -DskipTests package        :: build all modules and the runnable jar
mvnw.cmd test                       :: run tests
run-dev.bat                         :: start the dev profile on port 8081
```

The application logs OTP emails in development when `MAIL_MODE=console`. In
production, leave `app.mail.expose-otp=false` so codes are never returned by
the API.

---

## 10. Troubleshooting

| Symptom | Fix |
| --- | --- |
| Port already in use | run with `--server.port=8081` or set `PORT` |
| Flyway validation error | drop/recreate the schema locally, or align migrations |
| Hibernate schema validation failure | confirm Flyway ran and `ddl-auto` matches |
| OTP email not received | check `MAIL_*` variables and spam folder; in dev read the console |
| Images disappear after redeploy | `UPLOAD_DIR` must point at persistent storage |
| Map empty | the Leaflet CDN or `GEOCODING_ENABLED` may be blocked; users can still pin manually |

---

## 11. License and ownership

Built for Palm University College and the Ghanaian student-housing market.
Third-party services: Leaflet (BSD-2-Clause), OpenStreetMap/Nominatim usage
policy applies to geocoding and map tiles.
