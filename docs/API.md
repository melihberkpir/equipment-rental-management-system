# API Reference

Base URL for local development: `http://localhost:8080`

Interactive documentation is available at `/swagger-ui.html`, and the OpenAPI document is available at `/v3/api-docs`.

## Authentication

Register or sign in to receive a JWT, then send it with protected requests:

```http
Authorization: Bearer <accessToken>
```

Tokens expire after one hour by default. Public registration always creates a `CUSTOMER` account. The first `ADMIN` account is created from the `ADMIN_EMAIL` and `ADMIN_PASSWORD` settings.

## Endpoints

### Authentication

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | Register a customer |
| POST | `/api/auth/login` | Public | Sign in |

### Categories

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| GET | `/api/categories` | Authenticated | List categories |
| GET | `/api/categories/{id}` | Authenticated | Get a category |
| POST | `/api/categories` | ADMIN | Create a category |
| PUT | `/api/categories/{id}` | ADMIN | Update a category |
| DELETE | `/api/categories/{id}` | ADMIN | Delete an empty category |

### Equipment

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| GET | `/api/equipment` | Authenticated | Browse and filter equipment |
| GET | `/api/equipment/{id}` | Authenticated | Get equipment details |
| GET | `/api/equipment/{id}/availability` | Authenticated | Check a date range |
| POST | `/api/equipment` | ADMIN | Create equipment |
| PUT | `/api/equipment/{id}` | ADMIN | Update equipment |
| DELETE | `/api/equipment/{id}` | ADMIN | Delete equipment without history |

Equipment list filters: `categoryId`, `status`, `q` (name search), `minRate`, and `maxRate`.

Availability requires `startDate` and `endDate` in `YYYY-MM-DD` format. Its `reasons` field can contain `EQUIPMENT_NOT_ACTIVE`, `UNDER_MAINTENANCE`, and `ALREADY_RESERVED`.

### Maintenance

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| GET | `/api/equipment/{equipmentId}/maintenance` | Authenticated | List maintenance records |
| POST | `/api/equipment/{equipmentId}/maintenance` | ADMIN | Create a record |
| PUT | `/api/equipment/{equipmentId}/maintenance/{maintenanceId}` | ADMIN | Update a record |

### Reservations

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| POST | `/api/reservations` | Authenticated | Create a reservation |
| GET | `/api/reservations` | Customer: own, ADMIN: all | List reservations, optionally by `status` |
| GET | `/api/reservations/{id}` | Owner or ADMIN | Get a reservation |
| PATCH | `/api/reservations/{id}/status` | Owner or ADMIN | Change reservation status |
| GET | `/api/reservations/{id}/agreement` | Owner or ADMIN | Download the agreement PDF |

The agreement is available for `CONFIRMED` and `COMPLETED` reservations.

### Payments

| Method | Path | Access | Description |
| --- | --- | --- | --- |
| GET | `/api/payments` | ADMIN | List payments, optionally by `reservationId` or `status` |
| GET | `/api/payments/{id}` | ADMIN | Get a payment |

## Pagination and sorting

List endpoints accept these query parameters:

| Parameter | Default | Rules |
| --- | --- | --- |
| `page` | `0` | Zero-based page number |
| `limit` | `20` | Between 1 and 100 |
| `sort` | Resource default | `field,asc` or `field,desc` |

| Resource | Sortable fields | Default |
| --- | --- | --- |
| Categories | `id`, `name` | `name,asc` |
| Equipment | `id`, `name`, `dailyRate`, `status` | `name,asc` |
| Maintenance | `id`, `startDate`, `endDate`, `status` | `startDate,asc` |
| Reservations | `id`, `startDate`, `endDate`, `status`, `totalCost` | `startDate,asc` |
| Payments | `id`, `amount`, `paymentDate`, `status` | `paymentDate,asc` |

List responses use this structure:

```json
{
  "items": [],
  "page": 0,
  "limit": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

## Example workflow

Create a reservation:

```http
POST /api/reservations
Authorization: Bearer <customerToken>
Content-Type: application/json

{
  "equipmentId": 1,
  "startDate": "2030-03-10",
  "endDate": "2030-03-13"
}
```

Confirm it as an administrator:

```http
PATCH /api/reservations/1/status
Authorization: Bearer <adminToken>
Content-Type: application/json

{
  "status": "CONFIRMED"
}
```

Download the agreement:

```http
GET /api/reservations/1/agreement
Authorization: Bearer <customerToken>
```

## Errors

Errors use the problem details format:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "errors": {
    "email": "must be a well-formed email address"
  }
}
```

| Status | Meaning |
| --- | --- |
| 400 | Invalid input, dates, paging, or sorting |
| 401 | Missing or invalid token, or incorrect credentials |
| 403 | The user does not have permission |
| 404 | The resource does not exist |
| 409 | The request conflicts with current data or a business rule |
