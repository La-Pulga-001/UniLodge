# Deploying UniLodge

UniLodge ships as a single Docker image (`Dockerfile`) plus a PostgreSQL 18
database. Two hosting paths are supported:

| Path | Cost | Effort | Best for |
| --- | --- | --- | --- |
| **Railway** (recommended) | Hobby `$5/month` plus usage; Free Trial `$5` for 30 days | Low — Git push deploys | A live demo or small production launch |
| **Oracle Cloud Always Free** | `$0` forever | High — you manage the VM | A zero-cost deployment you can maintain |

> Railway's **Free** plan only includes `$1/month` of usage credit. A JVM
> service (about 0.5 GB) and PostgreSQL running 24/7 cost more than that, so
> use the 30-day Free Trial for a demo or the Hobby plan for anything
> permanent. The app never sleeps on Railway.

The container reads the port from `PORT` and exposes a health check at
`/api/universities`, so both paths work without code changes.

---

## 1. Deploy on Railway

### 1.1 Create the project

1. Sign in at <https://railway.com> and choose **New Project → Deploy from
   GitHub repo**.
2. Select `La-Pulga-001/UniLodge` and the `main` branch.
3. Railway reads `railway.json`, builds the `Dockerfile` and creates the
   `unilodge` service.
4. In the same project choose **New → Database → PostgreSQL**. Railway
   provisions a database service named `Postgres`.

### 1.2 Configure the application service

Open **unilodge → Variables → Raw Editor** and paste the following, replacing
`JWT_SECRET` with a fresh random value (`openssl rand -base64 48`):

```dotenv
SPRING_PROFILES_ACTIVE=prod
DB_HOST=${{Postgres.PGHOST}}
DB_PORT=${{Postgres.PGPORT}}
DB_NAME=${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
JWT_SECRET=replace-with-at-least-32-random-characters
APP_URL=https://your-service.up.railway.app
DEMO_DATA=true
MAIL_MODE=console
```

Railway substitutes the `${{Postgres.*}}` references with the database
service's values. If your database service has a different name, adjust the
references. `DB_URL` also works when it is a JDBC URL
(`jdbc:postgresql://host:port/database`); plain `postgresql://` URLs are not
accepted by the JDBC driver.

Save the variables. The service redeploys automatically.

### 1.3 Generate a public domain

1. Open the service → **Settings → Networking → Generate Domain**.
2. Copy the `https://…up.railway.app` address.
3. Set `APP_URL` to exactly that address and redeploy. `APP_URL` is used in
   OTP, password-reset and invitation links; the fallback is
   `http://localhost:8080`, which is wrong for a hosted instance.

Wait for the health check at `/api/universities` to pass. The first boot runs
Flyway migrations and seeds the demo data, so it can take a couple of minutes.

### 1.4 Demo content

`DEMO_DATA=true` seeds 18 approved listings (**8** on the home page), one
pending listing for the admin approval flow, student-board posts with
comments, and five demo users. Seeding is idempotent: restarting the service
adds anything missing and regenerates demo photos lost with the container
filesystem.

| Role | Email | Password |
| --- | --- | --- |
| Student | `kwame.student@unilodge.test` | `Demo@1234` |
| Student | `abena.student@unilodge.test` | `Demo@1234` |
| Student | `yaw.student@unilodge.test` | `Demo@1234` |
| Approved landlord | `ama.landlord@unilodge.test` | `Demo@1234` |
| Pending landlord | `kofi.landlord@unilodge.test` | `Demo@1234` |

Set `DEMO_DATA=false` and redeploy for an empty production catalogue.

### 1.5 Email

Without SMTP, `MAIL_MODE=console` writes emails — including OTP codes — to the
service log. Open **unilodge → Logs** and search for
`UniLodge mail (console transport)`.

To send real email, set:

```dotenv
MAIL_MODE=smtp
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your.address@gmail.com
MAIL_PASSWORD=your-16-character-app-password
MAIL_FROM=UniLodge <your.address@gmail.com>
```

Any SMTP provider works (Brevo, Resend, Mailgun, Zoho). Gmail needs 2-Step
Verification and an [App Password](https://myaccount.google.com/apppasswords).

### 1.6 Activate the original administrators

Three passwordless administrators are seeded on first boot:

- `fblesselikem@gmail.com`
- `ayensusamuel866@gmail.com`
- `deiyeshua05@gmail.com`

For each address, open `https://<your-domain>/admin-setup.html`, request a
setup code, then read the 6-digit code from the email or the service log and
choose a strong password. Only original administrators can invite or promote
other administrators.

### 1.7 Uploads and persistent storage

Container filesystems are ephemeral. Attach a Railway volume mounted at
`/app/uploads` to keep landlord photos across deploys. The entrypoint fixes
the volume's ownership automatically. Demo photos do not need the volume;
they are regenerated at boot.

### 1.8 Custom domain

Add the domain under **Settings → Networking → Custom Domain**, follow the DNS
instructions, then change `APP_URL` to the new `https://` address and redeploy.

---

## 2. Deploy on Oracle Cloud Always Free

This path uses one Always Free VM and Docker Compose. It costs nothing but
requires a credit card for identity verification and about 30 minutes of
setup. Oracle may reclaim idle Always Free compute; keep the instance active.

1. Create an **Always Free** Ubuntu 24.04 VM (Ampere A1 or AMD micro) in a
   region close to Ghana, for example Frankfurt or London. Note the public IP
   and add your SSH key.
2. Allow inbound TCP 22, 80 and 443 in the subnet's security list.
3. Install Docker:

   ```bash
   sudo apt-get update
   sudo apt-get install -y docker.io docker-compose-v2 git
   sudo usermod -aG docker "$USER"
   ```

   Log out and back in so the group change applies.

4. Clone the repository and create `.env` from the template:

   ```bash
   git clone https://github.com/La-Pulga-001/UniLodge.git
   cd UniLodge
   cp .env.example .env
   nano .env
   ```

   Set at least `DB_PASSWORD`, `JWT_SECRET`, `APP_URL` and `MAIL_*`.
   `docker-compose.yml` already wires the database and uploads volume, and
   runs the app on port 8080.

5. Start the stack:

   ```bash
   docker compose up -d --build
   docker compose logs -f app
   ```

6. Put HTTPS in front of it with Caddy:

   ```bash
   sudo apt-get install -y caddy
   sudo tee /etc/caddy/Caddyfile >/dev/null <<'EOF'
   unilodge.example.com {
       reverse_proxy 127.0.0.1:8080
   }
   EOF
   sudo systemctl reload caddy
   ```

   Point the domain's DNS `A` record at the VM IP first. Caddy obtains and
   renews the certificate automatically. Set `APP_URL` to the same `https://`
   address and restart with `docker compose up -d`.

7. Back up the database regularly:

   ```bash
   docker compose exec db pg_dump -U unilodge_app unilodge > unilodge-$(date +%F).sql
   ```

---

## 3. Environment variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | yes | `prod` in any hosted environment |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | yes | PostgreSQL connection; `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD` and `DB_URL` are also accepted |
| `JWT_SECRET` | yes | Signing key for sessions; at least 32 characters, never change it after users sign in |
| `APP_URL` | yes | Public `https://` address used in email links |
| `DEMO_DATA` | no | `true` seeds demo accounts, 18 listings and board posts (default in `prod`) |
| `MAIL_MODE` | no | `smtp` (default) or `console` to log emails |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | no | SMTP delivery |
| `UPLOAD_DIR` | no | Upload directory (Dockerfile sets `/app/uploads`) |
| `CORS_ORIGINS` | no | Comma-separated extra browser origins |

Passwords, `JWT_SECRET` and SMTP credentials belong in the platform's
variable store, never in Git.

---

## 4. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Health check never passes | Read the build/runtime log for Flyway or datasource errors. Confirm the database variables resolve and the database is in the same project. |
| `Connection refused` to PostgreSQL | The database is still provisioning or the `${{Postgres.*}}` service name is wrong. |
| Login works, OTP links are wrong | `APP_URL` is missing or still `http://localhost:8080`. Set the public URL and redeploy. |
| OTP email not received | Configure SMTP; meanwhile read the code from the service log. |
| Uploaded photos vanish after deploy | Expected without a volume. Mount one at `/app/uploads`. |
| Everyone is logged out after a deploy | `JWT_SECRET` changed. Restore the previous value. |
| Admin setup says the email is unknown | Confirm the address is in `app.bootstrap.admin-emails` and the profile is `prod`. |
| Railway service stops at the free credit limit | The Free plan only includes `$1/month`; upgrade to Hobby or use the Oracle path. |
