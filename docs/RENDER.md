# Deploying UniLodge on Render

UniLodge runs on Render as a Docker web service backed by Render Postgres.
`render.yaml` defines the whole stack, so the first deploy is a single
Blueprint apply.

- **Region:** Frankfurt (closest Render region to Ghana)
- **Web service:** `unilodge` — Docker, free plan by default
- **Database:** `unilodge-db` — Render Postgres, free plan by default
- **Health check:** `/api/universities`

> **Free plan limitations**
>
> - The web service sleeps after 15 minutes idle; the next request takes
>   roughly a minute to wake it.
> - Free Postgres databases **expire 30 days after creation**. Upgrade the
>   database before then or all data is lost.
> - Free services have no persistent disk, so uploaded photos disappear on
>   every deploy or restart. Seed/demo images are generated at boot and are
>   recreated automatically; landlord uploads are not.
>
> For a real launch, upgrade to a paid web instance with a disk and a paid
> Postgres plan. See [Upgrading](#upgrading-to-a-paid-setup).

---

## 1. Deploy the Blueprint

The repository must contain `render.yaml` on its default branch. It already
does at <https://github.com/La-Pulga-001/UniLodge>.

1. Sign in to <https://dashboard.render.com> with the GitHub account that can
   see the repository (`La-Pulga-001/UniLodge`).
2. Open the one-click Blueprint link:

   <https://render.com/deploy?repo=https://github.com/La-Pulga-001/UniLodge>

3. Choose a Blueprint name (for example `unilodge`) and confirm the two
   resources: the `unilodge` web service and the `unilodge-db` database.
4. Render prompts for the `MAIL_*` variables because they are marked
   `sync: false`:
   - Enter real SMTP credentials if you have them.
   - Or leave them blank. UniLodge still starts and writes outgoing email —
     including OTP codes — to the Render log until SMTP is configured. This
     lets the first administrator complete setup.
5. Click **Apply**. The first Docker build compiles the Maven project and
   usually takes 5–10 minutes (later builds use the cache and are faster).

When the deploy finishes, Render shows the public URL:
`https://unilodge-<hash>.onrender.com`. Open it — the catalogue should load
with Palm University College.

---

## 2. Configure email (recommended)

Open **unilodge → Environment** in the Render Dashboard and set:

| Variable | Example |
| --- | --- |
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `your.address@gmail.com` |
| `MAIL_PASSWORD` | a 16-character Google **App Password** (not the normal password) |
| `MAIL_FROM` | `UniLodge <your.address@gmail.com>` |

Save and let Render redeploy. Any SMTP provider works (Brevo, Resend,
Mailgun, Zoho, your host's mail server). Port 587 with STARTTLS is the
default.

Gmail requires 2-Step Verification and an App Password:
<https://myaccount.google.com/apppasswords>. A dedicated account such as
`no-reply@yourdomain.com` is better for a public launch.

Until SMTP is configured, retrieve setup codes from **Logs** in the Render
Dashboard and search for `UniLodge mail (console transport)`. Remove or
ignore those log entries after setup, and configure SMTP before inviting
real users.

---

## 3. Activate the original administrators

UniLodge seeds three passwordless original administrators on first boot:

- `fblesselikem@gmail.com`
- `ayensusamuel866@gmail.com`
- `deiyeshua05@gmail.com`

For each address:

1. Open `https://<your-service>.onrender.com/admin-setup.html`.
2. Request a setup code.
3. Enter the 6-digit code from the email (or from the Render log if SMTP is
   not configured) and choose a strong password.
4. Repeat for the other two addresses.

The account becomes an active, verified original administrator. Only original
administrators can invite or promote other administrators.

If you change the administrator list later, update
`app.bootstrap.admin-emails` in
`unilodge-web/src/main/resources/application.yml` and redeploy. Existing
accounts are never modified by the seeder.

---

## 4. Custom domain

1. In **unilodge → Settings → Custom Domains**, add the domain and follow the
   DNS instructions.
2. Set `APP_URL` to the public HTTPS address in **Environment**, for example
   `https://unilodge.example.com`. This value is used in OTP and invitation
   emails. Without it the app falls back to Render's `onrender.com` URL.

---

## 5. Upgrading to a paid setup

A paid web instance removes the idle sleep and allows a persistent disk. A
paid database removes the 30-day expiry.

1. Open the web service in Render, change its instance type to a paid plan,
   open the database and change its plan too.
2. Attach a disk to the web service:
   - Name: `unilodge-uploads`
   - Mount path: `/app/uploads`
   - Size: 1 GB is plenty to start
3. Update `render.yaml` so a future Blueprint sync does not downgrade or
   detach them:
   - Change `plan: free` to the plan ID Render shows for each resource, or
     delete the `plan:` lines (Render then keeps the current value on sync).
   - Uncomment the `disk:` block under the web service.
4. Commit and push. Render redeploys and keeps `/app/uploads` across future
   deploys.

The container entrypoint fixes disk ownership automatically before dropping
to the unprivileged `unilodge` user, so no manual `chown` is needed.

Free databases expire after 30 days; upgrade **before** the expiry date to
keep the data. Take a backup from the database's **Connect** menu if you need
one beforehand.

---

## 6. Deploys and logs

- Every push to the connected branch triggers a deploy
  (`autoDeployTrigger: commit`).
- Build and runtime logs: **unilodge → Logs**.
- Failed deploy: open the build log and look for Maven or Docker errors.
- Health check failures: confirm the deploy finished and the database is
  available. The app needs Flyway to finish before `/api/universities`
  returns 200.
- To redeploy without a code change: **Manual Deploy → Deploy latest commit**.

Useful environment variables:

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` (set by the Blueprint) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | wired from Render Postgres |
| `JWT_SECRET` | generated by Render; never change it after users sign in |
| `APP_URL` | public URL used in emails; falls back to `RENDER_EXTERNAL_URL` |
| `MAIL_MODE` | `smtp` (default) or `console` |
| `MAIL_*` | SMTP delivery |
| `UPLOAD_DIR` | `/app/uploads` (set in the Dockerfile) |
| `CORS_ORIGINS` | optional extra browser origins |

`JWT_SECRET` is generated once by Render. Changing it invalidates every
existing session, so leave it alone.

---

## 7. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `502` on first visit | Free service was asleep. Wait ~60 seconds and reload. |
| Deploy fails during Maven build | Check the build log. The Dockerfile needs `maven:3.9-eclipse-temurin-25`. |
| `Connection refused` to Postgres | The database is still provisioning, or the web service and database are in different regions. |
| Health check never passes | Check runtime logs for Flyway or datasource errors. |
| OTP email not received | Configure SMTP; meanwhile read the code from the Render log. |
| Uploaded images vanish after deploy | Expected on the free plan. Attach a disk on a paid plan. |
| Admin setup says the email is unknown | Confirm the address is in `app.bootstrap.admin-emails` and the deploy is on `prod`. |
| Everyone is logged out after a deploy | `JWT_SECRET` changed. Keep Render's generated value. |
