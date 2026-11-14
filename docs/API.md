# UniLodge API reference

Base URL: `http://localhost:8081` (development). All paths are prefixed with
`/api`. Responses are JSON unless noted.

Interactive OpenAPI documentation is available at `/swagger-ui.html`, and the
raw specification at `/v3/api-docs`.

---

## 1. Conventions

### Authentication

Send the token returned by login, registration or setup:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Tokens are valid for 168 hours by default. Every request re-loads the account,
so suspending a user invalidates access immediately. Anonymous requests may
read the public catalogue.

### Errors

```json
{
  "status": 400,
  "error": "VALIDATION_FAILED",
  "message": "Please correct the highlighted fields.",
  "timestamp": "2026-11-12T09:00:00Z",
  "fieldErrors": { "email": "must be a well-formed email address" }
}
```

| HTTP | Meaning |
| --- | --- |
| 400 | validation or flow error |
| 401 | missing/expired token |
| 403 | authenticated but not allowed (or suspended/pending) |
| 404 | resource not found |
| 409 | duplicate or conflicting state (e.g. email already registered) |
| 429 | OTP resend cooldown or too many attempts |
| 500 | unexpected server error |

### Pagination

List endpoints return:

```json
{
  "content": [],
  "page": 0,
  "size": 12,
  "totalElements": 0,
  "totalPages": 0,
  "last": true
}
```

`page` is zero-based. `size` is capped by each service (normally 100).

### Enumerations

| Enum | Values |
| --- | --- |
| `UserRole` | `STUDENT`, `LANDLORD`, `ADMIN` |
| `UserStatus` | `PENDING_APPROVAL`, `ACTIVE`, `REJECTED`, `SUSPENDED` |
| `ListingStatus` | `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `SUSPENDED`, `REMOVED` |
| `ModerationStatus` | `VISIBLE`, `HIDDEN`, `REMOVED` |
| `PropertyType` | `SINGLE_ROOM`, `SELF_CONTAINED`, `APARTMENT`, `STUDIO`, `HOSTEL`, `SHARED_HOUSE` |
| `FurnishedStatus` | `FURNISHED`, `SEMI_FURNISHED`, `UNFURNISHED` |
| `InvitationStatus` | `PENDING`, `ACCEPTED`, `REVOKED`, `EXPIRED` |
| `AuditAction` | see the audit endpoint response |

The exact enum lists are defined in `unilodge-common/src/main/java/com/unilodge/common/enums`.

### Date and money formats

- Instants: ISO-8601 UTC (`2026-11-12T09:00:00Z`)
- Dates: `YYYY-MM-DD`
- Money: decimal number in Ghana cedis, e.g. `1800.00` (never send a currency symbol)

---

## 2. Authentication and accounts

### `POST /api/auth/register` — public

Register a student or landlord. Landlords start as `PENDING_APPROVAL`.

```json
{
  "fullName": "Ama Mensah",
  "email": "ama@example.com",
  "password": "StrongPass123",
  "phone": "0244123456",
  "role": "LANDLORD",
  "universityId": "uuid",
  "businessName": "Ama Homes"
}
```

Response `AuthResponse`:

```json
{ "token": "...", "user": { "id": "uuid", "role": "LANDLORD", "...": "..." } }
```

### `POST /api/auth/login` — public

```json
{ "email": "ama@example.com", "password": "StrongPass123" }
```

Returns `AuthResponse`. Suspended accounts and rejected landlords are handled
per the status rules (rejected landlords may sign in and see their note;
suspended users cannot).

### `GET /api/auth/me` — authenticated

Returns the current `UserResponse`.

### `PUT /api/auth/me` — authenticated

```json
{
  "fullName": "Ama Mensah",
  "phone": "0244123456",
  "universityId": "uuid",
  "businessName": "Ama Homes"
}
```

### `POST /api/auth/me/avatar` — authenticated, multipart

Field `file`. JPEG/PNG/WebP, max 2 MB. Returns the updated `UserResponse`.

### Password reset

| Method | Path | Auth | Body |
| --- | --- | --- | --- |
| POST | `/api/auth/password/forgot` | public | `{ "email": "..." }` → `{ message, devCode? }` |
| POST | `/api/auth/password/verify` | public | `{ "email": "...", "code": "123456" }` → `{ resetToken, devCode? }` |
| POST | `/api/auth/password/reset` | public | `{ "resetToken": "...", "newPassword": "..." }` |
| POST | `/api/auth/password/change` | bearer | `{ "currentPassword": "...", "newPassword": "..." }` |

OTP rules: six digits, 10-minute lifetime, five attempts, 60-second resend
cooldown. `devCode` is returned only when `app.mail.expose-otp=true` (dev).

### Original-administrator setup

| Method | Path | Auth | Body |
| --- | --- | --- | --- |
| POST | `/api/auth/setup/request-code` | public | `{ "email": "..." }` → `{ message, devCode? }` |
| POST | `/api/auth/setup/complete` | public | `{ "email": "...", "code": "123456", "password": "...", "inviteToken": null }` |

Only the three configured original administrator emails that still lack a
password may use this flow. Completing it returns `AuthResponse`.

### Administrator invitations

| Method | Path | Auth | Body |
| --- | --- | --- | --- |
| GET | `/api/auth/invitations/{token}` | public | — → `InvitePreviewResponse` |
| POST | `/api/auth/invitations/{token}/code` | public | — → `{ message, devCode? }` |
| POST | `/api/auth/invitations/{token}/accept` | public | `{ "code": "123456", "password": "..." }` → `AuthResponse` |

Invitation tokens are 64 hex characters and expire after seven days.

### Promoted-administrator verification

| Method | Path | Auth | Body |
| --- | --- | --- | --- |
| POST | `/api/auth/admin-verification/request` | bearer (promoted admin) | — → `{ message, devCode? }` |
| POST | `/api/auth/admin-verification/confirm` | bearer (promoted admin) | `{ "code": "123456" }` → `AuthResponse` |

Until verification succeeds, administrator endpoints return `403`.

---

## 3. Universities

### `GET /api/universities` — public

Returns active institutions:

```json
[
  {
    "id": "uuid",
    "name": "Palm University College",
    "shortName": "Palm",
    "city": "Shai Hills",
    "region": "Greater Accra",
    "latitude": 5.9042,
    "longitude": -0.0722,
    "active": true
  }
]
```

Institution management is under `/api/admin/universities` (section 7).

---

## 4. Listings

### `GET /api/properties` — public

Search approved listings. Administrators may pass `status` to inspect any
state; for other callers the effective status is always `APPROVED`.

| Query | Type | Notes |
| --- | --- | --- |
| `q` | string | title/description/address search |
| `universityId` | uuid | institution filter |
| `minRent`, `maxRent` | decimal | GH₵ bounds |
| `furnished` | enum | `FURNISHED`, `SEMI_FURNISHED`, `UNFURNISHED` |
| `propertyType` | enum | see enumerations |
| `minBedrooms` | int | minimum bedrooms |
| `maxDistanceKm` | double | distance to the listing's institution |
| `landlordId` | uuid | listings owned by one landlord |
| `status` | enum | administrators only |
| `sort` | string | `newest` (default), `rent_asc`, `rent_desc`, `distance`, `popular` |
| `page`, `size` | int | defaults `0` and `12` |

Returns `PagedResponse<PropertySummaryDto>`.

### `GET /api/properties/featured` — public

Up to the eight most recent approved listings.

### `GET /api/properties/{id}` — public (contact data requires auth)

Returns `PropertyDto`:

```json
{
  "id": "uuid",
  "universityId": "uuid",
  "universityName": "Palm University College",
  "title": "Modern self-contained near Palm",
  "description": "...",
  "propertyType": "SELF_CONTAINED",
  "rentAmount": 1800.00,
  "negotiable": true,
  "utilitiesIncluded": false,
  "utilitiesDescription": "Prepaid meter",
  "furnished": "SEMI_FURNISHED",
  "bedrooms": 1,
  "bathrooms": 1,
  "address": "Shai Hills",
  "city": "Accra",
  "region": "Greater Accra",
  "latitude": 5.91,
  "longitude": -0.07,
  "distanceToCampusKm": 2.4,
  "moveInDate": "2026-12-01",
  "leaseTerms": "12 months",
  "amenities": ["Water", "Security"],
  "status": "APPROVED",
  "adminNote": null,
  "images": [
    { "id": "uuid", "url": "/api/files/property/uuid", "cover": true, "sortOrder": 0 }
  ],
  "coverImageUrl": "/api/files/property/uuid",
  "landlord": {
    "id": "uuid",
    "fullName": "Ama Mensah",
    "businessName": "Ama Homes",
    "email": "ama@example.com",
    "phone": "0244123456",
    "avatarUrl": null
  },
  "saved": false,
  "viewCount": 42,
  "createdAt": "2026-11-10T12:00:00Z",
  "updatedAt": "2026-11-10T12:00:00Z"
}
```

`landlord.email` and `landlord.phone` are `null` for anonymous callers.

### `GET /api/properties/mine` — authenticated landlord

`PagedResponse<PropertySummaryDto>` for the signed-in landlord, defaults
`page=0`, `size=50`.

### `GET /api/properties/saved` and `/saved/ids` — authenticated student

Saved listings (full summaries) or just their IDs. Landlord and administrator
accounts receive `403`.

### `POST /api/properties` — landlord

```json
{
  "title": "Modern self-contained near Palm",
  "description": "At least twenty characters describing the home.",
  "universityId": "uuid",
  "propertyType": "SELF_CONTAINED",
  "rentAmount": 1800.00,
  "negotiable": true,
  "utilitiesIncluded": false,
  "utilitiesDescription": "Prepaid meter",
  "furnished": "SEMI_FURNISHED",
  "bedrooms": 1,
  "bathrooms": 1,
  "address": "Shai Hills",
  "city": "Accra",
  "region": "Greater Accra",
  "latitude": 5.91,
  "longitude": -0.07,
  "moveInDate": "2026-12-01",
  "leaseTerms": "12 months",
  "amenities": ["Water", "Security"]
}
```

New listings receive `PENDING_APPROVAL`. Pending/rejected/suspended landlords
receive `403`.

### `PUT /api/properties/{id}` — owner

Same body as create. Any successful edit resets the listing to
`PENDING_APPROVAL` so an administrator re-reviews it.

### `DELETE /api/properties/{id}` — owner

Marks the listing `REMOVED`. It can be restored with
`POST /api/properties/{id}/restore`.

### Photos

| Method | Path | Auth | Body |
| --- | --- | --- | --- |
| POST | `/api/properties/{id}/images` | owner, multipart | `file` → `List<PropertyImageDto>` |
| PUT | `/api/properties/{id}/images/{imageId}/cover` | owner | — → `List<PropertyImageDto>` |
| DELETE | `/api/properties/{id}/images/{imageId}` | owner | — → `List<PropertyImageDto>` |

Limits: 10 images per listing, 5 MB per file, JPEG/PNG/WebP.

### `POST /api/properties/{id}/save` — authenticated

Toggles the saved state: `{ "saved": true }`.

---

## 5. Student posts and comments

The board is a private student space. Every endpoint below requires a signed-in
student or administrator; landlord accounts receive `403`.

### `GET /api/posts` — signed-in student or administrator

| Query | Type | Notes |
| --- | --- | --- |
| `q` | string | title/body search |
| `universityId` | uuid | institution filter |
| `authorId` | uuid | posts by one student |
| `status` | enum | administrators only; others see `VISIBLE` |
| `page`, `size` | int | defaults `0`, `12` |

Returns `PagedResponse<StudentPostDto>`:

```json
{
  "id": "uuid",
  "title": "Looking for a roommate",
  "body": "...",
  "preferredLocation": "Shai Hills",
  "budgetMax": 1500.00,
  "roommatesNeeded": 1,
  "status": "VISIBLE",
  "adminNote": null,
  "universityId": "uuid",
  "universityName": "Palm University College",
  "author": { "id": "uuid", "fullName": "Kwame", "avatarUrl": null, "role": "STUDENT" },
  "commentCount": 3,
  "saved": false,
  "createdAt": "2026-11-10T12:00:00Z",
  "updatedAt": "2026-11-10T12:00:00Z"
}
```

### `GET /api/posts/{id}` — signed-in student or administrator

Returns one `StudentPostDto`. Hidden/removed posts are only visible to
administrators and the author.

### `POST /api/posts` — student

```json
{
  "title": "Looking for a roommate",
  "body": "At least ten characters about your search.",
  "universityId": "uuid",
  "preferredLocation": "Shai Hills",
  "budgetMax": 1500.00,
  "roommatesNeeded": 1
}
```

### `PUT /api/posts/{id}` and `DELETE /api/posts/{id}` — author

Update or remove your own post. Removal sets `REMOVED`.

### `GET /api/posts/saved` and `/saved/ids` — student

Saved posts, or their IDs.

### `POST /api/posts/{id}/save` — student

Toggles save state: `{ "saved": true }`.

### Comments

| Method | Path | Auth | Body |
| --- | --- | --- | --- |
| GET | `/api/posts/{postId}/comments` | student or admin | — → `PagedResponse<CommentDto>` |
| POST | `/api/posts/{postId}/comments` | student or admin | `{ "body": "..." }` → `CommentDto` |
| DELETE | `/api/comments/{commentId}` | author | — → `{ "message": "..." }` |

Comment body: 1–2000 characters.

---

## 6. Files and geocoding

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| GET | `/api/files/property/{imageId}` | public | property photo, cached 30 days |
| GET | `/api/files/avatar/{userId}` | public | avatar, `no-cache` |
| GET | `/api/geo/search?q=...` | public | Nominatim proxy → `List<GeocodeResult>` |

`GeocodeResult`: `{ "displayName": "...", "latitude": 5.9, "longitude": -0.07 }`.
Geocoding is rate-limited by the upstream provider; the listing form also
allows manual pin placement.

---

## 7. Administration

Every route below requires a bearer token for an administrator whose email is
verified (`adminVerified=true`). Otherwise the API returns `403`.

### Statistics

`GET /api/admin/stats`

```json
{
  "totalStudents": 120,
  "totalLandlords": 18,
  "pendingLandlords": 2,
  "suspendedUsers": 0,
  "totalProperties": 45,
  "pendingProperties": 3,
  "approvedProperties": 40,
  "rejectedProperties": 2,
  "totalPosts": 31,
  "hiddenPosts": 1,
  "totalComments": 74,
  "totalAdmins": 3,
  "originalAdmins": 3,
  "newUsersLast7Days": 12,
  "totalSavedProperties": 88,
  "generatedAt": "2026-11-12T09:00:00Z"
}
```

### Users

`GET /api/admin/users?role=LANDLORD&status=ACTIVE&q=ama&page=0&size=20`

Returns `PagedResponse<AdminUserDto>`.

| Method | Path | Body | Effect |
| --- | --- | --- | --- |
| POST | `/api/admin/users/{id}/approve-landlord` | — | sets landlord `ACTIVE` |
| POST | `/api/admin/users/{id}/reject-landlord` | `{ "note": "..." }` | sets `REJECTED` |
| POST | `/api/admin/users/{id}/suspend` | `{ "note": "..." }` | blocks sign-in |
| POST | `/api/admin/users/{id}/reactivate` | — | clears suspension |
| POST | `/api/admin/users/{id}/promote` | — | promotes to `ADMIN`, requires verification |
| POST | `/api/admin/users/{id}/revoke-admin` | — | demotes (see guards below) |

Promotion/revocation guards:

- only original administrators may promote or revoke
- an admin cannot revoke themselves
- the last active administrator cannot be removed
- the last original administrator cannot be removed
- administrators cannot be suspended

### Listings

| Method | Path | Body | Effect |
| --- | --- | --- | --- |
| GET | `/api/admin/properties?status=PENDING_APPROVAL&q=&page=0&size=20` | — | moderation queue |
| POST | `/api/admin/properties/{id}/approve` | — | publishes |
| POST | `/api/admin/properties/{id}/reject` | `{ "note": "..." }` | rejects with note |
| POST | `/api/admin/properties/{id}/suspend` | `{ "note": "..." }` | hides an approved listing |
| POST | `/api/admin/properties/{id}/restore` | — | restores and publishes (`APPROVED`) |

### Posts and comments

| Method | Path | Body | Effect |
| --- | --- | --- | --- |
| GET | `/api/admin/posts?status=VISIBLE&q=&page=0&size=20` | — | post list |
| POST | `/api/admin/posts/{id}/hide` | `{ "note": "..." }` | hides a post |
| POST | `/api/admin/posts/{id}/restore` | — | restores visibility |
| GET | `/api/admin/posts/{postId}/comments?page=0&size=50` | — | comments incl. hidden |
| POST | `/api/admin/comments/{id}/hide` | — | hides a comment |
| POST | `/api/admin/comments/{id}/remove` | — | removes a comment |

### Administrator governance

| Method | Path | Body/result |
| --- | --- | --- |
| GET | `/api/admin/admins` | `List<AdminUserDto>` |
| GET | `/api/admin/invitations` | `List<AdminInvitationDto>` |
| POST | `/api/admin/invitations` | `{ "email": "...", "fullName": "..." }` → `AdminInvitationDto` |
| DELETE | `/api/admin/invitations/{id}` | `{ "message": "..." }` |

Sending an invitation creates a placeholder `ADMIN` account with no password
and returns an invitation row. The invitation link is
`{APP_URL}/admin-setup.html?invite={token}` and is delivered by email.

### Institutions

| Method | Path | Body |
| --- | --- | --- |
| GET | `/api/admin/universities` | all institutions, including inactive |
| POST | `/api/admin/universities` | `UniversityRequest` |
| PUT | `/api/admin/universities/{id}` | `UniversityRequest` |

```json
{
  "name": "University of Ghana",
  "shortName": "UG",
  "city": "Legon",
  "region": "Greater Accra",
  "latitude": 5.6505,
  "longitude": -0.1867,
  "active": true
}
```

### Audit log

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/admin/audit?page=0&size=30` | `PagedResponse<AuditLogDto>` |
| GET | `/api/admin/audit/recent` | latest actions for the dashboard |

```json
{
  "id": "uuid",
  "actorName": "Admin Name",
  "action": "PROPERTY_APPROVED",
  "targetType": "PROPERTY",
  "targetId": "uuid",
  "targetLabel": "Modern self-contained near Palm",
  "details": null,
  "createdAt": "2026-11-12T09:00:00Z"
}
```

The full `AuditAction` list lives in
`unilodge-common/src/main/java/com/unilodge/common/enums/AuditAction.java`.

---

## 8. Quick cURL example

```bash
# Sign in
curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"kwame.student@unilodge.test","password":"Demo@1234"}'

# Search approved listings
curl -s "http://localhost:8081/api/properties?universityId=UUID&sort=rent_asc&maxRent=2000"

# Read a listing as a signed-in user (includes landlord contact)
curl -s http://localhost:8081/api/properties/UUID \
  -H "Authorization: Bearer TOKEN"
```
