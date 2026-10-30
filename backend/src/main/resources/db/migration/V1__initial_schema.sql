-- Bookwell initial schema.
-- The exclusion constraints below require btree_gist for UUID equality.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(320) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    phone           VARCHAR(30),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_email_normalized CHECK (email = lower(email)),
    CONSTRAINT users_role_check CHECK (role IN ('CUSTOMER', 'PROVIDER', 'ADMIN')),
    CONSTRAINT users_full_name_check CHECK (length(trim(full_name)) > 0)
);

CREATE INDEX idx_users_role ON users (role);

CREATE TABLE provider_profiles (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    display_name    VARCHAR(140) NOT NULL,
    business_name   VARCHAR(160) NOT NULL,
    category        VARCHAR(60) NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    city            VARCHAR(100) NOT NULL DEFAULT '',
    address         VARCHAR(240) NOT NULL DEFAULT '',
    phone           VARCHAR(30),
    time_zone       VARCHAR(64) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    featured        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT provider_profiles_user_unique UNIQUE (user_id),
    CONSTRAINT provider_profiles_user_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT provider_profiles_status_check CHECK (status IN ('PENDING', 'APPROVED', 'SUSPENDED')),
    CONSTRAINT provider_profiles_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT provider_profiles_name_check CHECK (length(trim(display_name)) > 0)
);

CREATE INDEX idx_provider_profiles_status ON provider_profiles (status);
CREATE INDEX idx_provider_profiles_category ON provider_profiles (category);
CREATE INDEX idx_provider_profiles_city ON provider_profiles (lower(city));

CREATE TABLE services (
    id                  UUID PRIMARY KEY,
    provider_id         UUID NOT NULL,
    name                VARCHAR(140) NOT NULL,
    description         TEXT NOT NULL DEFAULT '',
    duration_minutes    INTEGER NOT NULL,
    price               NUMERIC(10, 2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT services_provider_fk FOREIGN KEY (provider_id) REFERENCES provider_profiles (id),
    CONSTRAINT services_duration_check CHECK (duration_minutes BETWEEN 5 AND 720),
    CONSTRAINT services_price_check CHECK (price >= 0),
    CONSTRAINT services_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT services_name_check CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_services_provider_active ON services (provider_id, active);
CREATE INDEX idx_services_provider_sort ON services (provider_id, sort_order, name);

CREATE TABLE weekly_schedules (
    id              UUID PRIMARY KEY,
    provider_id     UUID NOT NULL,
    day_of_week     SMALLINT NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    start_minute    INTEGER GENERATED ALWAYS AS ((EXTRACT(EPOCH FROM start_time)::integer / 60)) STORED,
    end_minute      INTEGER GENERATED ALWAYS AS ((EXTRACT(EPOCH FROM end_time)::integer / 60)) STORED,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT weekly_schedules_provider_fk FOREIGN KEY (provider_id) REFERENCES provider_profiles (id) ON DELETE CASCADE,
    CONSTRAINT weekly_schedules_day_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT weekly_schedules_time_check CHECK (start_time < end_time),
    CONSTRAINT weekly_schedules_no_overlap EXCLUDE USING gist (
        provider_id WITH =,
        day_of_week WITH =,
        int4range(start_minute, end_minute, '[)') WITH &&
    )
);

CREATE INDEX idx_weekly_schedules_provider_day ON weekly_schedules (provider_id, day_of_week);

CREATE TABLE schedule_exceptions (
    id              UUID PRIMARY KEY,
    provider_id     UUID NOT NULL,
    exception_date  DATE NOT NULL,
    type            VARCHAR(24) NOT NULL,
    start_time      TIME,
    end_time        TIME,
    reason          VARCHAR(240),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT schedule_exceptions_provider_fk FOREIGN KEY (provider_id) REFERENCES provider_profiles (id) ON DELETE CASCADE,
    CONSTRAINT schedule_exceptions_type_check CHECK (type IN ('CLOSED', 'BLOCKED', 'EXTRA_AVAILABILITY')),
    CONSTRAINT schedule_exceptions_time_check CHECK (
        (type = 'CLOSED' AND start_time IS NULL AND end_time IS NULL)
        OR
        (type IN ('BLOCKED', 'EXTRA_AVAILABILITY') AND start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time)
    )
);

CREATE UNIQUE INDEX uq_schedule_exceptions_closed
    ON schedule_exceptions (provider_id, exception_date)
    WHERE type = 'CLOSED';
CREATE INDEX idx_schedule_exceptions_provider_date ON schedule_exceptions (provider_id, exception_date);

CREATE TABLE appointments (
    id                          UUID PRIMARY KEY,
    provider_id                 UUID NOT NULL,
    customer_id                 UUID NOT NULL,
    service_id                  UUID NOT NULL,
    start_ts                    TIMESTAMPTZ NOT NULL,
    end_ts                      TIMESTAMPTZ NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    service_name                VARCHAR(140) NOT NULL,
    service_duration_minutes    INTEGER NOT NULL,
    service_price               NUMERIC(10, 2) NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    customer_name               VARCHAR(120) NOT NULL,
    customer_email              VARCHAR(320) NOT NULL,
    customer_phone              VARCHAR(30),
    customer_notes              VARCHAR(1000),
    cancellation_reason         VARCHAR(500),
    cancelled_at                TIMESTAMPTZ,
    cancelled_by                VARCHAR(20),
    rescheduled_from_ts         TIMESTAMPTZ,
    reschedule_count            INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT appointments_provider_fk FOREIGN KEY (provider_id) REFERENCES provider_profiles (id),
    CONSTRAINT appointments_customer_fk FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT appointments_service_fk FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT appointments_time_check CHECK (end_ts > start_ts),
    CONSTRAINT appointments_status_check CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT appointments_price_check CHECK (service_price >= 0),
    CONSTRAINT appointments_duration_check CHECK (service_duration_minutes BETWEEN 5 AND 720),
    CONSTRAINT appointments_reschedule_count_check CHECK (reschedule_count >= 0),
    CONSTRAINT appointments_cancelled_by_check CHECK (cancelled_by IS NULL OR cancelled_by IN ('CUSTOMER', 'PROVIDER', 'ADMIN', 'SYSTEM')),
    CONSTRAINT appointments_cancellation_fields_check CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
        OR
        (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancelled_by IS NULL)
    )
);

CREATE INDEX idx_appointments_provider_start ON appointments (provider_id, start_ts);
CREATE INDEX idx_appointments_customer_start ON appointments (customer_id, start_ts);
CREATE INDEX idx_appointments_status_start ON appointments (status, start_ts);
CREATE INDEX idx_appointments_service ON appointments (service_id);

-- Half-open [start, end) intervals prevent false conflicts at touching boundaries.
-- Cancelled appointments stop blocking because they are excluded from the predicate.
ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlap
    EXCLUDE USING gist (
        provider_id WITH =,
        tstzrange(start_ts, end_ts, '[)') WITH &&
    )
    WHERE (status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW'));

CREATE TABLE refresh_token_sessions (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    family_id       UUID NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by     UUID,
    user_agent      VARCHAR(255),
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT refresh_token_sessions_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_token_sessions_hash_unique UNIQUE (token_hash),
    CONSTRAINT refresh_token_sessions_expiry_check CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_sessions_user ON refresh_token_sessions (user_id);
CREATE INDEX idx_refresh_sessions_family ON refresh_token_sessions (family_id);
CREATE INDEX idx_refresh_sessions_expiry ON refresh_token_sessions (expires_at);

CREATE TABLE notifications (
    id                  UUID PRIMARY KEY,
    appointment_id      UUID NOT NULL,
    type                VARCHAR(32) NOT NULL,
    recipient_email     VARCHAR(320) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at        TIMESTAMPTZ NOT NULL,
    attempts            INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 5,
    last_error          VARCHAR(1000),
    sent_at             TIMESTAMPTZ,
    delivery_mode       VARCHAR(20),
    dedupe_key          VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT notifications_appointment_fk FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE,
    CONSTRAINT notifications_type_check CHECK (type IN ('BOOKING_CONFIRMATION', 'CANCELLATION', 'RESCHEDULE', 'REMINDER')),
    CONSTRAINT notifications_status_check CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED')),
    CONSTRAINT notifications_attempts_check CHECK (attempts >= 0 AND attempts <= max_attempts)
);

CREATE UNIQUE INDEX uq_notifications_dedupe ON notifications (dedupe_key);
CREATE INDEX idx_notifications_due ON notifications (status, scheduled_at);
CREATE INDEX idx_notifications_appointment ON notifications (appointment_id);
