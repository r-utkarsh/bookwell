# Bookwell API

Base path: `/api/v1`  
Content type: `application/json`  
Timestamps: ISO-8601 UTC instants (for example `2027-03-05T04:30:00Z`)  
Date-only values: ISO local dates (`2027-03-05`) interpreted in the provider's time zone

Interactive documentation is available when the backend is running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `GET /actuator/health`

## Authentication model

- **Access token**: short-lived JWT (15 minutes by default), sent as `Authorization: Bearer <token>`. The frontend keeps it in memory only.
- **Refresh token**: opaque random token in the `bookwell_refresh` HttpOnly cookie, scoped to `/api/v1/auth`. Only a SHA-256 hash is stored in PostgreSQL. Each refresh rotates the token; reuse of an old token revokes the whole session family.
- **CSRF**: required only for the cookie-backed `POST /auth/refresh` and `POST /auth/logout` endpoints. First call `GET /auth/csrf`; send the returned token in the returned header name (`X-XSRF-TOKEN`). The same value is also available as the non-HttpOnly `XSRF-TOKEN` cookie.
- **Logout** is public at the HTTP layer because it relies on the refresh cookie plus CSRF; it always clears the cookie.

### Auth endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Create a customer account. |
| `POST` | `/auth/register/provider` | Public | Create a provider user and a `PENDING` provider profile. |
| `POST` | `/auth/login` | Public | Authenticate and receive an access token plus refresh cookie. |
| `POST` | `/auth/refresh` | Refresh cookie + CSRF | Rotate the refresh token and issue a new access token. |
| `POST` | `/auth/logout` | Refresh cookie + CSRF | Revoke the current refresh session and clear the cookie. |
| `GET` | `/auth/csrf` | Public | Return `{ "headerName": "...", "token": "..." }`. |
| `GET` | `/auth/me` | Bearer | Return the current user summary. |

`POST /auth/register` body:

```json
{
  "email": "customer@example.com",
  "password": "A-strong-password1",
  "fullName": "Riya Sharma",
  "phone": "+91 91234 56780"
}
```

`POST /auth/register/provider` accepts the same fields plus `businessName`, `displayName`, `category`, `city`, and `timeZone`. New providers are **not publicly bookable** until an administrator approves them.

Successful registration/login/refresh response:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "3f18...",
    "email": "customer@example.com",
    "fullName": "Riya Sharma",
    "role": "CUSTOMER",
    "enabled": true,
    "providerId": null,
    "providerStatus": null
  }
}
```

Passwords must be 10–72 printable characters, contain at least one letter and one number, and contain no spaces. Login, customer registration, and provider registration are rate-limited per IP (10 requests/minute by default; `429 RATE_LIMITED` with `Retry-After: 60`).

## Public discovery and availability

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/providers` | Public | Paginated approved-provider search. |
| `GET` | `/providers/categories` | Public | Distinct categories for approved providers. |
| `GET` | `/providers/{providerId}` | Public | Profile, active services, and weekly hours. |
| `GET` | `/providers/{providerId}/availability` | Public | Dynamic slots for a service and local date. |

`GET /providers` query parameters: `page` (default `0`), `size` (default `12`, max 50), `q` (name, business, city, or service text), `category`.

`GET /providers/{providerId}/availability?serviceId=<uuid>&date=2027-03-05` returns:

```json
{
  "providerId": "…",
  "serviceId": "…",
  "serviceName": "Signature Haircut",
  "date": "2027-03-05",
  "timeZone": "Asia/Kolkata",
  "durationMinutes": 45,
  "price": 899.00,
  "currency": "INR",
  "slots": [
    { "startAt": "2027-03-05T04:30:00Z", "endAt": "2027-03-05T05:15:00Z", "label": "10:00 AM" }
  ],
  "message": null
}
```

Slots are generated from weekly intervals, exceptions, the service duration, existing active appointments, minimum lead time, start interval, and maximum advance window. A candidate must fit **entirely** inside a permitted interval. `message` explains why a day has no slots (closed, fully booked, past date, beyond horizon, etc.).

## Bookings

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/appointments` | `CUSTOMER` | Create a confirmed appointment. |
| `GET` | `/appointments?scope=upcoming\|history&page=0&size=10` | `CUSTOMER` | Own upcoming or historical bookings. |
| `GET` | `/appointments/{id}` | Owner, provider, or admin | Appointment detail with computed permissions. |
| `POST` | `/appointments/{id}/cancel` | Owner, provider, or admin | Cancel. Body `{ "reason": "..." }` is optional. |
| `POST` | `/appointments/{id}/reschedule` | Owner, provider, or admin | Atomic move to another offered slot. |
| `POST` | `/appointments/{id}/complete` | `PROVIDER` or `ADMIN` | Mark a finished confirmed appointment completed. |
| `POST` | `/appointments/{id}/no-show` | `PROVIDER` or `ADMIN` | Mark a started confirmed appointment as no-show. |

Create request:

```json
{
  "providerId": "8b1e...",
  "serviceId": "90af...",
  "startAt": "2027-03-05T04:30:00Z",
  "notes": "Please use fragrance-free products."
}
```

The backend re-validates the requested start against the live schedule and then inserts. If a concurrent request wins the slot, PostgreSQL's exclusion constraint raises `23P01`, translated to:

```json
{
  "timestamp": "2027-03-01T10:15:30Z",
  "status": 409,
  "code": "SLOT_UNAVAILABLE",
  "message": "That time was just booked by someone else. Please choose another available time.",
  "path": "/api/v1/appointments"
}
```

The frontend refreshes availability after this conflict.

Cancellation/rescheduling rules:

- Customers can change bookings only up to the configured cutoff before start (`PT2H` by default).
- Providers can change their own bookings until the appointment starts.
- Admins can override the time window.
- Only `CONFIRMED` appointments are modifiable.
- A reschedule must be a currently offered slot, must change the time, and must preserve the snapshotted duration.
- Rescheduling is a single database update. The same exclusion constraint protects the new interval, so there is no window with two active bookings.

## Provider studio

All routes require a `PROVIDER` access token and operate only on the authenticated provider's own profile.

| Method | Path | Description |
|---|---|---|
| `GET` | `/provider/profile` | Own dashboard profile. |
| `PUT` | `/provider/profile` | Update display/business details, contact, time zone. |
| `GET` | `/provider/stats` | Upcoming, today, active-service, and completed counts. |
| `GET` | `/provider/services` | All own services, including inactive. |
| `POST` | `/provider/services` | Create a service. |
| `PUT` | `/provider/services/{id}` | Update a service (name, description, duration, price, active, sort order). |
| `DELETE` | `/provider/services/{id}` | Soft-deactivate a service; returns `204`. |
| `GET` | `/provider/schedule?from=YYYY-MM-DD` | Weekly intervals, upcoming exceptions, time zone. |
| `PUT` | `/provider/schedule/weekly` | Replace all weekly intervals atomically. |
| `POST` | `/provider/schedule/exceptions` | Add closed day, blocked interval, or extra availability. |
| `DELETE` | `/provider/schedule/exceptions/{id}` | Remove an exception. |
| `GET` | `/provider/appointments?from=&to=&status=` | Calendar appointments (max 93-day range). |

Weekly schedule request:

```json
{
  "intervals": [
    { "dayOfWeek": 2, "startTime": "10:00", "endTime": "13:00" },
    { "dayOfWeek": 2, "startTime": "14:00", "endTime": "19:00" }
  ]
}
```

`dayOfWeek` is 1 (Monday) through 7 (Sunday). Overlapping intervals for the same day are rejected by a PostgreSQL exclusion constraint as well as service validation. Service durations are 5–720 minutes; prices are non-negative decimals with at most two fractional digits.

Exception request:

```json
{
  "date": "2027-03-18",
  "type": "BLOCKED",
  "startTime": "12:00",
  "endTime": "15:00",
  "reason": "Supplier visit"
}
```

Types: `CLOSED` (no times), `BLOCKED` (remove a period), `EXTRA_AVAILABILITY` (add a period outside normal weekly hours). Only one `CLOSED` exception per provider/date is allowed.

## Administration

All routes require an `ADMIN` access token. Administrators are provisioned with environment-configured bootstrap credentials, never public registration.

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/stats` | Platform counts. |
| `GET` | `/admin/providers?q=&status=&page=&size=` | Provider search and approval queue. |
| `PATCH` | `/admin/providers/{id}/status` | Body `{ "status": "APPROVED" }`; also `PENDING`/`SUSPENDED`. |
| `GET` | `/admin/customers?q=&enabled=&page=&size=` | Customer search. |
| `PATCH` | `/admin/customers/{id}/status` | Body `{ "enabled": false }` to disable sign-in/bookings. |
| `GET` | `/admin/bookings?providerId=&customerId=&status=&from=&to=&page=&size=` | Platform booking ledger. |
| `GET` | `/admin/notifications?status=&type=&page=&size=` | Notification outbox. |
| `POST` | `/admin/notifications/{id}/retry` | Requeue a `FAILED` notification. |

Admin searches are bounded: bookings default to the last 30 days through the next 90 days and a maximum span of 366 days.

## Errors

All handled errors share one shape:

```json
{
  "timestamp": "2027-03-01T10:15:30Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Please check the highlighted fields.",
  "path": "/api/v1/appointments",
  "fieldErrors": [
    { "field": "startAt", "message": "Start time is required." }
  ]
}
```

`fieldErrors` is present only for validation failures. Stack traces and raw database errors are never returned.

| Status | Typical codes | Meaning |
|---|---|---|
| `400` | `VALIDATION_FAILED`, `INVALID_REQUEST`, `PAST_APPOINTMENT`, `INVALID_SCOPE`, `INVALID_DATE_RANGE` | Invalid input or business rule. |
| `401` | `UNAUTHORIZED` | Missing, expired, or invalid access token. |
| `403` | `FORBIDDEN` | Authenticated but not allowed, or CSRF rejected. |
| `404` | `NOT_FOUND` | Resource does not exist or is not visible to the caller. |
| `409` | `SLOT_UNAVAILABLE`, `CANCELLATION_CUTOFF`, `PROVIDER_NOT_BOOKABLE`, `SERVICE_INACTIVE`, `STALE_RESOURCE` | Conflict with current state. |
| `429` | `RATE_LIMITED` | Too many auth attempts; retry after 60 seconds. |
| `500` | `INTERNAL_ERROR` | Safe generic failure. |

Ownership is enforced in the service layer: customers can only read/modify their own appointments, providers only their own profile/services/schedule/appointments, and admins only via `/admin/**`.
