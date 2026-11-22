# Domain Documentation

## Entity relationship diagram

```mermaid
erDiagram
    ROLES ||--o{ USERS : assigned_to
    USERS ||--o{ RESERVATIONS : makes
    CATEGORIES ||--o{ EQUIPMENT : groups
    EQUIPMENT ||--o{ RESERVATIONS : booked_in
    EQUIPMENT ||--o{ MAINTENANCE_RECORDS : has
    RESERVATIONS ||--o{ PAYMENTS : records

    ROLES {
        bigint role_id PK
        varchar name UK
    }
    USERS {
        bigint user_id PK
        bigint role_id FK
        varchar full_name
        varchar email UK
        varchar password_hash
    }
    CATEGORIES {
        bigint category_id PK
        varchar name UK
        text description
    }
    EQUIPMENT {
        bigint equipment_id PK
        bigint category_id FK
        varchar name
        text description
        numeric daily_rate
        varchar status
    }
    RESERVATIONS {
        bigint reservation_id PK
        bigint user_id FK
        bigint equipment_id FK
        date start_date
        date end_date
        varchar status
        numeric total_cost
    }
    PAYMENTS {
        bigint payment_id PK
        bigint reservation_id FK
        numeric amount
        timestamptz payment_date
        varchar status
    }
    MAINTENANCE_RECORDS {
        bigint maintenance_id PK
        bigint equipment_id FK
        text description
        date start_date
        date end_date
        varchar status
    }
```

## Status values

| Entity | Values |
| --- | --- |
| Equipment | `ACTIVE`, `INACTIVE`, `MAINTENANCE` |
| Reservation | `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED` |
| Payment | `PENDING`, `PAID`, `FAILED`, `REFUNDED` |
| Maintenance | `SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |

The application currently records payments as `PAID` and `REFUNDED`. `PENDING` and `FAILED` are reserved in the schema for future payment workflows.

## Reservation lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING: Customer creates
    PENDING --> CONFIRMED: Admin confirms
    PENDING --> CANCELLED: Owner or admin cancels
    CONFIRMED --> COMPLETED: Admin completes
    CONFIRMED --> CANCELLED: Owner or admin cancels
    CANCELLED --> [*]
    COMPLETED --> [*]
```

## Reservation workflow

```mermaid
sequenceDiagram
    actor Customer
    participant API as REST API
    participant Service as Reservation Service
    participant DB as PostgreSQL
    participant PDF as Agreement Service

    Customer->>API: POST /api/reservations
    API->>Service: Validated request
    Service->>DB: Lock equipment and check availability
    DB-->>Service: Availability result
    Service->>DB: Save PENDING reservation
    Service-->>API: Reservation
    API-->>Customer: 201 Created
    Note over Customer,DB: An administrator confirms the reservation
    Customer->>API: GET /api/reservations/{id}/agreement
    API->>PDF: Generate agreement
    PDF->>DB: Load reservation details
    PDF-->>API: PDF document
    API-->>Customer: application/pdf
```

## Business rules

### Reservations

- A reservation requires `start_date` before `end_date`, and new reservations cannot start in the past.
- The end date is the return date and is not charged. A rental from March 10 to March 13 is three days.
- The backend calculates `total_cost` as `daily_rate * rental days`.
- Equipment must be `ACTIVE` and free of blocking maintenance and overlapping reservations.
- `PENDING` and `CONFIRMED` reservations block their dates. A new rental may start on another rental's return date.
- An equipment row lock serializes booking requests, and a PostgreSQL exclusion constraint guarantees that blocking reservations cannot overlap.

### Status changes and cancellation

- Customers can access only their own reservations and can only cancel them before the start date.
- Administrators can confirm, complete, and cancel reservations.
- A reservation cannot be confirmed while its equipment is not active.
- A reservation cannot be completed before its start date.
- `CANCELLED` and `COMPLETED` are final.
- Changing equipment to `INACTIVE` does not cancel existing reservations.

### Payments

- No external payment provider is integrated.
- Confirming a reservation records a `PAID` payment for the full amount.
- Cancelling a confirmed reservation changes that payment to `REFUNDED`.
- Only administrators can view payments.

### Maintenance

- `SCHEDULED` and `IN_PROGRESS` records block availability.
- Maintenance dates are inclusive. An empty end date means the maintenance is open-ended.
- Blocking maintenance cannot overlap a `PENDING` or `CONFIRMED` reservation.

### Catalogue

- `daily_rate` must be non-negative.
- Categories containing equipment cannot be deleted.
- Equipment with reservation or maintenance history cannot be deleted. Set it to `INACTIVE` instead.

### Accounts

- Passwords are stored only as BCrypt hashes.
- Email addresses are unique and compared without regard to case.
- Public registration cannot assign the `ADMIN` role.
