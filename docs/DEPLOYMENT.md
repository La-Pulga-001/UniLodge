# Deploying UniLodge

UniLodge ships as a single Docker image (`Dockerfile`) plus a PostgreSQL 18
database. Three hosting paths are supported:

| Path | Cost | Effort | Best for |
| --- | --- | --- | --- |
| **Northflank Sandbox** (recommended free) | `$0`, no card, `$20/month` resource allowance | Low — Git deploys, managed Postgres | A free demo that stays online. See [NORTHFLANK.md](NORTHFLANK.md) |
| **Railway** | Hobby `$5/month` plus usage; Free Trial `$5` for 30 days | Low — Git push deploys | A live demo or small production launch |
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

One Always Free VM runs PostgreSQL, the application and Caddy. Caddy
terminates HTTPS and renews certificates automatically. The stack is
`docker-compose.prod.yml`; `deploy/oracle-setup.sh` installs Docker and opens
the host firewall.

This path costs nothing, but Oracle asks for a credit card for identity
verification, and Ampere A1 capacity is sometimes unavailable at peak times.

### 2.1 Create the VM

1. In the OCI console open **Compute → Instances → Create instance**.
2. Name it `unilodge`, choose **Canonical Ubuntu 24.04**, and select the
   **Ampere A1.Flex** shape with 2 OCPU / 12 GB (or 4 OCPU / 24 GB, still
   Always Free).
3. Under **Networking**, assign a public IPv4 address and add your SSH public
   key. Note the public IP.
4. Create the instance. If A1 capacity is unavailable, retry later, try
   another availability domain, or fall back to `VM.Standard.E2.1.Micro`
   (the setup script adds 2 GB of swap to make the build fit).
5. Open **Networking → Virtual Cloud Networks → your VCN → Security Lists →
   Default Security List** and add ingress rules for TCP **80** and **443**
   from `0.0.0.0/0`. Keep the existing SSH rule on port 22.

### 2.2 Install Docker

```bash
ssh -i ~/.ssh/your-key ubuntu@<PUBLIC_IP>
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/La-Pulga-001/UniLodge.git
cd UniLodge
sudo bash deploy/oracle-setup.sh
newgrp docker        # or log out and back in
```

The script installs Docker Engine and the Compose plugin, adds `ubuntu` to
the `docker` group, opens ports 80/443 in the host firewall, and adds swap on
small VMs.

### 2.3 Configure the environment

```bash
cp .env.example .env
openssl rand -base64 48   # use this as JWT_SECRET
openssl rand -base64 24   # use this as DB_PASSWORD
nano .env
```

Set at least:

```dotenv
DB_PASSWORD=<generated>
JWT_SECRET=<generated>
SITE_ADDRESS=unilodge.<PUBLIC_IP>.sslip.io
ACME_EMAIL=you@example.com
APP_URL=https://unilodge.<PUBLIC_IP>.sslip.io
DEMO_DATA=true
MAIL_MODE=console
```

`sslip.io` is a free wildcard DNS service: the hostname above resolves to the
IP embedded in it, so Caddy can obtain a real certificate without buying a
domain. If you own a domain, point its `A` record at the VM and use that name
instead. For a plain-HTTP IP-only demo set `SITE_ADDRESS=:80` and
`APP_URL=http://<PUBLIC_IP>`.

`APP_URL` must match the final `https://` address exactly; it is used in OTP,
password-reset and invitation links.

### 2.4 Start the stack

```bash
sudo docker compose -f docker-compose.prod.yml up -d --build
sudo docker compose -f docker-compose.prod.yml logs -f app
```

The first build takes roughly 5–15 minutes on an A1 VM and longer on a micro
instance. Wait for `Started UniLodgeApplication`, then open
`https://<SITE_ADDRESS>`. The first HTTPS request can take a few seconds while
Caddy obtains the certificate.

Only Caddy publishes ports; PostgreSQL and the application stay on the
private Docker network. Uploads and database files live in named volumes and
survive redeploys.

### 2.5 Demo content, email and administrators

The demo accounts, 8 featured listings and student-board posts described in
sections 1.4–1.6 work the same way. Open
`https://<SITE_ADDRESS>/admin-setup.html` for the three original
administrators. With `MAIL_MODE=console`, read the OTP codes from:

```bash
sudo docker compose -f docker-compose.prod.yml logs app | grep -A 12 "UniLodge mail"
```

After configuring SMTP in `.env`, apply it with
`sudo docker compose -f docker-compose.prod.yml up -d`.

### 2.6 Updates

```bash
cd ~/UniLodge
git pull
sudo docker compose -f docker-compose.prod.yml up -d --build
```

### 2.7 Backups

`deploy/backup.sh` writes a compressed dump to `backups/` and keeps 14 days:

```bash
cd ~/UniLodge
bash deploy/backup.sh
```

Schedule it daily with `crontab -e` (as `ubuntu`):

```cron
15 3 * * * cd /home/ubuntu/UniLodge && bash deploy/backup.sh >> /home/ubuntu/unilodge-backup.log 2>&1
```

Restore with the app stopped:

```bash
sudo docker compose -f docker-compose.prod.yml stop app
sudo docker compose -f docker-compose.prod.yml exec -T db \
  pg_restore -U unilodge_app -d unilodge --clean --if-exists < backups/unilodge-YYYY-MM-DD-HHMM.dump
sudo docker compose -f docker-compose.prod.yml start app
```

Copy backups off the VM regularly; a lost boot volume takes its backups with
it.

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
| `SITE_ADDRESS` | production Compose only | Hostname Caddy serves; the domain, an `sslip.io` name, or `:80` |
| `ACME_EMAIL` | production Compose only | Let's Encrypt contact address |
| `JAVA_TOOL_OPTIONS` | production Compose only | JVM flags; defaults to a 768 MB heap cap |

Passwords, `JWT_SECRET` and SMTP credentials belong in the platform's
variable store, never in Git.

---

## 4. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| Health check never passes (Railway) | Read the build/runtime log for Flyway or datasource errors. Confirm the database variables resolve and the database is in the same project. |
| `Connection refused` to PostgreSQL | The database is still provisioning, or the `${{Postgres.*}}` service name is wrong. |
| Login works, OTP links are wrong | `APP_URL` is missing or still `http://localhost:8080`. Set the public URL and redeploy. |
| OTP email not received | Configure SMTP; meanwhile read the code from the service log. |
| Uploaded photos vanish after deploy | Expected without a volume. Mount one at `/app/uploads`. |
| Everyone is logged out after a deploy | `JWT_SECRET` changed. Restore the previous value. |
| Admin setup says the email is unknown | Confirm the address is in `app.bootstrap.admin-emails` and the profile is `prod`. |
| Railway service stops at the free credit limit | The Free plan only includes `$1/month`; upgrade to Hobby or use the Oracle path. |
| Oracle site unreachable on port 80/443 | Allow TCP 80 and 443 in the OCI security list, then check `sudo iptables -L INPUT -n`. |
| Caddy cannot obtain a certificate | `SITE_ADDRESS` must resolve to the VM's public IP and ports 80/443 must be reachable. Check `docker compose -f docker-compose.prod.yml logs caddy`. |
| Docker build killed on an AMD micro VM | The 1 GB instance is out of memory. Use an Ampere A1 shape or confirm the setup script added swap. |
| `permission denied` running Docker | Log out and back in after `deploy/oracle-setup.sh`, or run `newgrp docker`. |
