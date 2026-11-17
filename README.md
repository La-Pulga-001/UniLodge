# UniLodge.

UniLodge is a/an/ana/banger student-accommodation platform for Ghana. Students browse approved
listings near their university, compare rent and distance, save favourites,
post roommate requests, comment, and contact landlords directly. Landlords
publish listings after administrator approval. Administrators moderate
accounts, listings and posts and manage each other through an audited
governance flow.

The first supported institution is **Palm University College** (Shai Hills,
Greater Accra). Further institutions can be added from the admin workspace
without redeploying.

- Currency: Ghana cedi (GH₵)
- Payments: none. UniLodge only provides direct call/WhatsApp/email contact.
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
docs/DEPLOYMENT.md     Railway and Oracle Cloud deployment guide
railway.json           Railway build/health-check configuration
Dockerfile             multi-stage production image
docker-compose.yml     local PostgreSQL + application stack
docker-compose.prod.yml production VM stack with Caddy HTTPS
deploy/                Oracle Cloud setup, Caddy config and backup scripts
docker-entrypoint.sh   fixes mounted-disk ownership, drops privileges
docker-compose.yml     PostgreSQL + application stack
.env.example           environment template for Docker Compose
run-dev.bat            local Windows launcher (port 8081)
```

---

## 2. Roles and rules

**Student**
- Browse, search, filter and sort approved listings
- Save listings and posts
- Create, edit and delete their own roommate posts
- Comment on posts and delete their own comments
- Contact landlords (phone, WhatsApp, email) when signed in

**Landlord**
- Browse and search approved listings
- Create and edit their own listings
- Upload up to 10 photos per listing (JPEG, PNG or WebP, max 5 MB each)
- Pin an exact location on the Leaflet/OpenStreetMap picker
- Cannot publish until an administrator approves the landlord account
- Every new or edited listing is reset to `PENDING_APPROVAL`
- No access to saved homes or the student board; those are student-only spaces

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

## 3. License and ownership

Built for Palm University College and the Ghanaian student-housing market.
Third-party services: Leaflet (BSD-2-Clause), OpenStreetMap/Nominatim usage
policy applies to geocoding and map tiles.

---

## 4. Deployment

The production image is built from `Dockerfile` and needs a PostgreSQL
database. [docs/RENDER.md](docs/RENDER.md) is the current deployment guide
(free Render Blueprint, demo accounts, the 30-day database limit and the Neon
migration path). [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) also covers
[Northflank Sandbox](docs/NORTHFLANK.md) (free, no card, does not sleep),
Railway and Oracle Cloud Always Free, plus environment variables, the `APP_URL`
setting used in email links, and the administrator setup flow.

Demo accounts use the password `Demo@1234`:

| Role | Email |
| --- | --- |
| Student | `kwame.student@unilodge.test` |
| Student | `abena.student@unilodge.test` |
| Student | `yaw.student@unilodge.test` |
| Approved landlord | `ama.landlord@unilodge.test` |
| Pending landlord | `kofi.landlord@unilodge.test` |

Set `DEMO_DATA=false` in production for an empty catalogue. The original
administrators (`fblesselikem@gmail.com`, `ayensusamuel866@gmail.com`,
`deiyeshua05@gmail.com`) activate their accounts at `/admin-setup.html`.
