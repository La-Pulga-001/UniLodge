# Deploying UniLodge on Northflank (free, no card)

Northflank's **Sandbox** plan is a strong free alternative to Render: it needs
no credit card, does not sleep, and includes 2 services, 1 database and up to
`$20/month` of compute. UniLodge uses one service and one PostgreSQL addon, so
it fits.

| | Northflank Sandbox | Render Free | Oracle Always Free |
| --- | --- | --- | --- |
| Card required | No | No | Yes (verification) |
| Sleeps when idle | No | Yes | No |
| Free database | 1 addon | 1 (expires) | Self-hosted |
| Effort | Low | Low | High |
| Main limit | `$20/month` resource allowance | 750 instance hours + bandwidth | Capacity/verification |

> Sandbox is intended for demos and small projects, not production SLAs. If the
> project grows, upgrade the account; nothing in the app needs to change.

The container already reads `PORT`, exposes port `8080`, runs Flyway
migrations on boot and serves the health endpoint `/api/universities`.

---

## 1. Create the account and project

1. Sign up at <https://app.northflank.com/signup> with GitHub. No payment
   method is requested for Sandbox.
2. Create a project named `unilodge` and pick the **Europe (Frankfurt)**
   region. It is the closest region to Ghana and is available on Sandbox.
3. Open the project's **Sandbox** usage page. It shows the monthly resource
   allowance and the projected cost, which is the number to watch.

## 2. Add PostgreSQL

1. In the project choose **Add addon → PostgreSQL**.
2. Name it `unilodge-db`, choose the smallest plan, and create it.
3. Open the addon and copy the **connection details**: host, port, database
   name, user and password. You will paste these into the service in step 4.

The app works with PostgreSQL 16 or 17; 17 is the closest match to the version
used locally and avoids Flyway's newer-server warning.

## 3. Create the application service

1. **Create service → Build from repository** (or "Deploy from Git").
2. Connect GitHub and select `La-Pulga-001/UniLodge`, branch `main`.
3. Build type: **Dockerfile**. Build context `/`, Dockerfile `Dockerfile`.
4. Compute plan: start with **0.5 vCPU / 1 GB**. If the build or boot is slow
   or the dashboard shows headroom, move to 1 vCPU / 2 GB. Keep the projected
   monthly cost under `$20` so the addon fits too.
5. Add a port: name `http`, port `8080`, protocol `HTTP`, **public**.
6. Health check: HTTP `GET /api/universities` on port `8080`. Set the initial
   delay to at least **120 seconds**; the first boot runs migrations and seeds
   the demo catalogue, which takes a minute or two.

Deploy. The first build is a Maven build inside the image and takes several
minutes.

## 4. Environment variables

Open the service → **Environment** and add:

```dotenv
SPRING_PROFILES_ACTIVE=prod
DB_HOST=<addon host>
DB_PORT=<addon port>
DB_NAME=<addon database>
DB_USERNAME=<addon user>
DB_PASSWORD=<addon password>
JWT_SECRET=<output of: openssl rand -base64 48>
APP_URL=https://<public service address>
DEMO_DATA=true
MAIL_MODE=console
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -Djava.awt.headless=true
```

`DB_URL` also works instead of the five `DB_*` values, as long as it is a JDBC
URL: `jdbc:postgresql://<host>:<port>/<database>?sslmode=require`.
Plain `postgresql://` URLs are rejected by the JDBC driver.

Redeploy after saving. The container starts as root only long enough to fix the
uploads directory, then drops to the `unilodge` user, so no extra user setting
is needed.

## 5. Public address and `APP_URL`

1. Open the service → **Networking** and copy the generated `https://` address.
2. Set `APP_URL` to exactly that address (no trailing slash) and redeploy.
   `APP_URL` is embedded in OTP, password-reset and invitation links; if it is
   missing, links point at `http://localhost:8080`.

If you later attach a custom domain, update `APP_URL` to the new address and
redeploy. HTTPS is terminated by Northflank, so Caddy is not used here.

## 6. Demo content and logins

`DEMO_DATA=true` seeds 18 approved listings (**8** on the home page), one
pending listing for the admin approval flow, student-board posts with comments,
and five users. Seeding is idempotent: restarts add anything missing and
regenerate demo photos lost with the container filesystem.

| Role | Email | Password |
| --- | --- | --- |
| Student | `kwame.student@unilodge.test` | `Demo@1234` |
| Student | `abena.student@unilodge.test` | `Demo@1234` |
| Student | `yaw.student@unilodge.test` | `Demo@1234` |
| Approved landlord | `ama.landlord@unilodge.test` | `Demo@1234` |
| Pending landlord | `kofi.landlord@unilodge.test` | `Demo@1234` |

## 7. Email and administrators

With `MAIL_MODE=console`, emails and OTP codes are written to the service log.
Open **Logs** and search for `UniLodge mail (console transport)`.

To send real email, set `MAIL_MODE=smtp` plus `MAIL_HOST`, `MAIL_PORT`,
`MAIL_USERNAME`, `MAIL_PASSWORD` and `MAIL_FROM`. Any SMTP provider works
(Brevo, Resend, Mailgun, Zoho, Gmail app password).

The three original administrators are seeded on first boot:

- `fblesselikem@gmail.com`
- `ayensusamuel866@gmail.com`
- `deiyeshua05@gmail.com`

Open `https://<your-domain>/admin-setup.html`, request a setup code, read it
from the email or the log, and choose a password. Only original administrators
can invite or promote other administrators.

## 8. Uploads and updates

The container filesystem is ephemeral. Demo photos regenerate at boot, but real
landlord uploads disappear on redeploy unless you mount a volume at
`/app/uploads`. Sandbox does not include a volume on the free allowance, so for
a demo this is acceptable.

Every push to `main` can rebuild the service automatically. To update manually,
open the service and choose **Redeploy** on the latest commit.

## 9. Troubleshooting

| Symptom | Fix |
| --- | --- |
| Build fails at `mvn package` | Check the build log; the Dockerfile needs the whole repo at the context root. |
| Health check timeout on first boot | Raise the initial delay to 180 s; Flyway plus demo seeding is slow on 0.5 vCPU. |
| `Connection refused` to PostgreSQL | Recheck the addon host/port and that the addon is in the same project and region. |
| Login works but links point to localhost | `APP_URL` is unset or wrong. |
| `OutOfMemoryError` during seeding | Move to 1 vCPU / 2 GB, or set `DEMO_DATA=false` for an empty catalogue. |
| Everyone logged out after redeploy | `JWT_SECRET` changed. Keep it stable. |
| Uploaded photos vanish | Expected without a volume. Demo photos are regenerated. |
| Monthly allowance nearly used | Lower the plan, or keep demo traffic light; Sandbox is a demo tier. |
