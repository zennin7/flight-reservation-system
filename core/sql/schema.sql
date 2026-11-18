-- ============================================================
-- FLIGHT RESERVATION SYSTEM — SCHEMA v1
-- ============================================================
CREATE TYPE user_role AS ENUM ('ADMIN', 'AGENT', 'CUSTOMER');
CREATE TYPE seat_class AS ENUM ('FIRST', 'ECONOMY');
CREATE TYPE seat_status AS ENUM ('AVAILABLE', 'HELD', 'BOOKED');
CREATE TYPE flight_status AS ENUM ('SCHEDULED', 'BOARDING', 'DEPARTED', 'CANCELLED');
CREATE TYPE booking_status AS ENUM ('PENDING', 'CONFIRMED', 'CANCELLED', 'REFUNDED');
CREATE TYPE payment_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED');

-- ---------- USERS / RBAC ----------
CREATE TABLE users (
    user_id         BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  UNIQUE NOT NULL,
    password_hash   VARCHAR(60)  NOT NULL,       -- BCrypt fixed length
    role            user_role    NOT NULL DEFAULT 'CUSTOMER',
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(120) UNIQUE NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- AIRPORTS ----------
CREATE TABLE airports (
    airport_id      SERIAL PRIMARY KEY,
    iata_code       CHAR(3) UNIQUE NOT NULL,
    name            VARCHAR(120) NOT NULL,
    city            VARCHAR(80)  NOT NULL,
    country         VARCHAR(80)  NOT NULL,
    timezone        VARCHAR(50)  NOT NULL
);

-- ---------- SEAT CLASS PRICING MULTIPLIERS ----------
CREATE TABLE seat_class_pricing (
    seat_class      seat_class PRIMARY KEY,
    price_multiplier NUMERIC(4,2) NOT NULL CHECK (price_multiplier > 0)
);
INSERT INTO seat_class_pricing VALUES
    ('FIRST', 3.50),
    ('ECONOMY', 1.00);

-- ---------- AIRCRAFT TYPES ----------
CREATE TABLE aircraft_types (
    aircraft_type_id SERIAL PRIMARY KEY,
    model           VARCHAR(60) NOT NULL,        -- e.g. 'Airbus A320'
    total_rows      SMALLINT NOT NULL CHECK (total_rows > 0),
    grid_width      SMALLINT NOT NULL CHECK (grid_width > 0) -- total GridPane columns incl. aisle gap
);

-- ---------- SEAT TEMPLATE (the physical cabin layout, reusable) ----------
-- This is what renders the "Visual Cabin Experience" grid.
CREATE TABLE seat_templates (
    seat_template_id BIGSERIAL PRIMARY KEY,
    aircraft_type_id SMALLINT NOT NULL REFERENCES aircraft_types(aircraft_type_id) ON DELETE CASCADE,
    seat_row        SMALLINT NOT NULL,           -- actual airline row number, e.g. 12
    seat_col        CHAR(1)  NOT NULL,            -- actual airline seat letter, e.g. 'C'
    seat_class      seat_class NOT NULL,
    display_row     SMALLINT NOT NULL,           -- GridPane row index (0-based)
    display_col     SMALLINT NOT NULL,           -- GridPane col index (0-based, aisle = skipped col)
    is_window       BOOLEAN NOT NULL DEFAULT FALSE,
    is_aisle        BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (aircraft_type_id, seat_row, seat_col),
    UNIQUE (aircraft_type_id, display_row, display_col)
);
CREATE INDEX idx_seat_templates_aircraft ON seat_templates(aircraft_type_id);

-- ---------- FLIGHTS ----------
CREATE TABLE flights (
    flight_id       BIGSERIAL PRIMARY KEY,
    flight_number   VARCHAR(10) NOT NULL,
    aircraft_type_id SMALLINT NOT NULL REFERENCES aircraft_types(aircraft_type_id),
    origin_airport_id SMALLINT NOT NULL REFERENCES airports(airport_id),
    dest_airport_id   SMALLINT NOT NULL REFERENCES airports(airport_id) CHECK (dest_airport_id <> origin_airport_id),
    departure_time  TIMESTAMPTZ NOT NULL,
    arrival_time    TIMESTAMPTZ NOT NULL CHECK (arrival_time > departure_time),
    base_price      NUMERIC(10,2) NOT NULL CHECK (base_price > 0),
    status          flight_status NOT NULL DEFAULT 'SCHEDULED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_flights_route_date ON flights(origin_airport_id, dest_airport_id, departure_time);

-- ---------- FLIGHT SEATS (per-flight live booking state) ----------
-- Instantiated (batch-inserted) from seat_templates when a flight is created.
CREATE TABLE flight_seats (
    flight_seat_id  BIGSERIAL PRIMARY KEY,
    flight_id       BIGINT NOT NULL REFERENCES flights(flight_id) ON DELETE CASCADE,
    seat_template_id BIGINT NOT NULL REFERENCES seat_templates(seat_template_id),
    status          seat_status NOT NULL DEFAULT 'AVAILABLE',
    held_until      TIMESTAMPTZ,                  -- soft-lock while user is in checkout flow
    UNIQUE (flight_id, seat_template_id)
);
CREATE INDEX idx_flight_seats_flight_status ON flight_seats(flight_id, status);

-- ---------- BOOKINGS ----------
CREATE TABLE bookings (
    booking_id      BIGSERIAL PRIMARY KEY,
    booking_ref     VARCHAR(10) UNIQUE NOT NULL,   -- e.g. 'PNR7X2QA1'
    user_id         BIGINT NOT NULL REFERENCES users(user_id),
    flight_id       BIGINT NOT NULL REFERENCES flights(flight_id),
    status          booking_status NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(10,2) NOT NULL CHECK (total_amount >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- BOOKING <-> SEAT (many seats per booking, group travel) ----------
CREATE TABLE booking_seats (
    booking_seat_id BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    flight_seat_id  BIGINT NOT NULL UNIQUE REFERENCES flight_seats(flight_seat_id),
    passenger_name  VARCHAR(120) NOT NULL,
    passport_number VARCHAR(20)  NOT NULL,
    date_of_birth   DATE NOT NULL,
    price_paid      NUMERIC(10,2) NOT NULL CHECK (price_paid >= 0)
);

-- ---------- PAYMENTS ----------
CREATE TABLE payments (
    payment_id      BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    amount          NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    method          VARCHAR(30) NOT NULL,
    status          payment_status NOT NULL DEFAULT 'PENDING',
    transaction_ref VARCHAR(60),
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- DYNAMIC PRICE VIEW ----------
-- Single source of truth for "base_price * class multiplier" — query this, never hardcode pricing in Java.
CREATE VIEW seat_pricing_view AS
SELECT
    fs.flight_seat_id,
    fs.flight_id,
    st.seat_row,
    st.seat_col,
    st.seat_class,
    st.display_row,
    st.display_col,
    fs.status,
    ROUND(f.base_price * scp.price_multiplier, 2) AS calculated_price
FROM flight_seats fs
JOIN seat_templates st ON st.seat_template_id = fs.seat_template_id
JOIN flights f ON f.flight_id = fs.flight_id
JOIN seat_class_pricing scp ON scp.seat_class = st.seat_class;
