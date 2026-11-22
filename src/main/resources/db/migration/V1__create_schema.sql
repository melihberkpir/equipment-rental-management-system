CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE roles (
    role_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name    VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users (
    user_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id       BIGINT       NOT NULL REFERENCES roles (role_id),
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL
);

CREATE TABLE categories (
    category_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE equipment (
    equipment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id  BIGINT         NOT NULL REFERENCES categories (category_id),
    name         VARCHAR(150)   NOT NULL,
    description  TEXT,
    daily_rate   NUMERIC(10, 2) NOT NULL CHECK (daily_rate >= 0),
    status       VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE'))
);

CREATE TABLE reservations (
    reservation_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users (user_id),
    equipment_id   BIGINT         NOT NULL REFERENCES equipment (equipment_id),
    start_date     DATE           NOT NULL,
    end_date       DATE           NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    total_cost     NUMERIC(12, 2) NOT NULL CHECK (total_cost >= 0),
    CONSTRAINT chk_reservation_dates CHECK (start_date < end_date),
    -- End date is exclusive, so one rental may start on the day another ends.
    CONSTRAINT excl_reservation_overlap EXCLUDE USING gist (
        equipment_id WITH =,
        daterange(start_date, end_date, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'CONFIRMED'))
);

CREATE TABLE payments (
    payment_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT         NOT NULL REFERENCES reservations (reservation_id),
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    payment_date   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED'))
);

CREATE TABLE maintenance_records (
    maintenance_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    equipment_id   BIGINT      NOT NULL REFERENCES equipment (equipment_id),
    description    TEXT        NOT NULL,
    start_date     DATE        NOT NULL,
    end_date       DATE,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_maintenance_dates CHECK (end_date IS NULL OR start_date <= end_date)
);

CREATE INDEX idx_users_role ON users (role_id);
CREATE INDEX idx_equipment_category ON equipment (category_id);
CREATE INDEX idx_reservations_user ON reservations (user_id);
CREATE INDEX idx_payments_reservation ON payments (reservation_id);
CREATE INDEX idx_maintenance_equipment ON maintenance_records (equipment_id, start_date);
