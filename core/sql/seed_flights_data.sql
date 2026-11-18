-- ============================================================
-- SKYVOYAGE FLIGHT RESERVATION SYSTEM — SEED FLIGHTS DATA
-- ============================================================
-- Populates:
-- 1. Airports (JNB, CPT)
-- 2. Aircraft Type (Boeing 737-800)
-- 3. Seat Templates (First Class 2x2 rows 1-2, Economy 3x3 rows 6-11)
-- 4. 4 Flights JNB -> CPT with varying times and base prices
-- 5. Flight Seats for each flight (~1/3 marked BOOKED/TAKEN)
--
-- Safe to re-run on an existing database schema.
-- ============================================================

BEGIN;

-- 1. Airports
INSERT INTO airports (iata_code, name, city, country, timezone)
VALUES
    ('JNB', 'O. R. Tambo International Airport', 'Johannesburg', 'South Africa', 'Africa/Johannesburg'),
    ('CPT', 'Cape Town International Airport', 'Cape Town', 'South Africa', 'Africa/Johannesburg')
ON CONFLICT (iata_code) DO NOTHING;

-- 2. Aircraft Type: Boeing 737-800
INSERT INTO aircraft_types (aircraft_type_id, model, total_rows, grid_width)
VALUES (1, 'Boeing 737-800', 8, 7)
ON CONFLICT (aircraft_type_id) DO UPDATE
    SET model = EXCLUDED.model,
        total_rows = EXCLUDED.total_rows,
        grid_width = EXCLUDED.grid_width;

-- Ensure sequence matches max ID
SELECT setval('aircraft_types_aircraft_type_id_seq', GREATEST((SELECT MAX(aircraft_type_id) FROM aircraft_types), 1));

-- 3. Seat Templates
-- First Class (2x2): Rows 1, 2. Columns A, B (left) and C, D (right). Aisle at col 2.
-- Display rows 0, 1.
INSERT INTO seat_templates (aircraft_type_id, seat_row, seat_col, seat_class, display_row, display_col, is_window, is_aisle)
VALUES
    -- Row 1 First Class (A, C, D, F at cols 0, 2, 4, 6)
    (1, 1, 'A', 'FIRST', 0, 0, TRUE,  FALSE),
    (1, 1, 'C', 'FIRST', 0, 2, FALSE, TRUE),
    (1, 1, 'D', 'FIRST', 0, 4, FALSE, TRUE),
    (1, 1, 'F', 'FIRST', 0, 6, TRUE,  FALSE),
    -- Row 2 First Class (A, C, D, F at cols 0, 2, 4, 6)
    (1, 2, 'A', 'FIRST', 1, 0, TRUE,  FALSE),
    (1, 2, 'C', 'FIRST', 1, 2, FALSE, TRUE),
    (1, 2, 'D', 'FIRST', 1, 4, FALSE, TRUE),
    (1, 2, 'F', 'FIRST', 1, 6, TRUE,  FALSE)
ON CONFLICT (aircraft_type_id, seat_row, seat_col) DO UPDATE
    SET seat_class = EXCLUDED.seat_class,
        display_row = EXCLUDED.display_row,
        display_col = EXCLUDED.display_col,
        is_window   = EXCLUDED.is_window,
        is_aisle    = EXCLUDED.is_aisle;

-- Economy (3x3): Rows 6, 7, 8, 9, 10, 11. Columns A, B, C (left) and D, E, F (right). Aisle at col 3.
-- Display rows 2 to 7.
INSERT INTO seat_templates (aircraft_type_id, seat_row, seat_col, seat_class, display_row, display_col, is_window, is_aisle)
VALUES
    -- Row 6 Economy
    (1, 6, 'A', 'ECONOMY', 2, 0, TRUE,  FALSE),
    (1, 6, 'B', 'ECONOMY', 2, 1, FALSE, FALSE),
    (1, 6, 'C', 'ECONOMY', 2, 2, FALSE, TRUE),
    (1, 6, 'D', 'ECONOMY', 2, 4, FALSE, TRUE),
    (1, 6, 'E', 'ECONOMY', 2, 5, FALSE, FALSE),
    (1, 6, 'F', 'ECONOMY', 2, 6, TRUE,  FALSE),
    -- Row 7 Economy
    (1, 7, 'A', 'ECONOMY', 3, 0, TRUE,  FALSE),
    (1, 7, 'B', 'ECONOMY', 3, 1, FALSE, FALSE),
    (1, 7, 'C', 'ECONOMY', 3, 2, FALSE, TRUE),
    (1, 7, 'D', 'ECONOMY', 3, 4, FALSE, TRUE),
    (1, 7, 'E', 'ECONOMY', 3, 5, FALSE, FALSE),
    (1, 7, 'F', 'ECONOMY', 3, 6, TRUE,  FALSE),
    -- Row 8 Economy
    (1, 8, 'A', 'ECONOMY', 4, 0, TRUE,  FALSE),
    (1, 8, 'B', 'ECONOMY', 4, 1, FALSE, FALSE),
    (1, 8, 'C', 'ECONOMY', 4, 2, FALSE, TRUE),
    (1, 8, 'D', 'ECONOMY', 4, 4, FALSE, TRUE),
    (1, 8, 'E', 'ECONOMY', 4, 5, FALSE, FALSE),
    (1, 8, 'F', 'ECONOMY', 4, 6, TRUE,  FALSE),
    -- Row 9 Economy
    (1, 9, 'A', 'ECONOMY', 5, 0, TRUE,  FALSE),
    (1, 9, 'B', 'ECONOMY', 5, 1, FALSE, FALSE),
    (1, 9, 'C', 'ECONOMY', 5, 2, FALSE, TRUE),
    (1, 9, 'D', 'ECONOMY', 5, 4, FALSE, TRUE),
    (1, 9, 'E', 'ECONOMY', 5, 5, FALSE, FALSE),
    (1, 9, 'F', 'ECONOMY', 5, 6, TRUE,  FALSE),
    -- Row 10 Economy
    (1, 10, 'A', 'ECONOMY', 6, 0, TRUE,  FALSE),
    (1, 10, 'B', 'ECONOMY', 6, 1, FALSE, FALSE),
    (1, 10, 'C', 'ECONOMY', 6, 2, FALSE, TRUE),
    (1, 10, 'D', 'ECONOMY', 6, 4, FALSE, TRUE),
    (1, 10, 'E', 'ECONOMY', 6, 5, FALSE, FALSE),
    (1, 10, 'F', 'ECONOMY', 6, 6, TRUE,  FALSE),
    -- Row 11 Economy
    (1, 11, 'A', 'ECONOMY', 7, 0, TRUE,  FALSE),
    (1, 11, 'B', 'ECONOMY', 7, 1, FALSE, FALSE),
    (1, 11, 'C', 'ECONOMY', 7, 2, FALSE, TRUE),
    (1, 11, 'D', 'ECONOMY', 7, 4, FALSE, TRUE),
    (1, 11, 'E', 'ECONOMY', 7, 5, FALSE, FALSE),
    (1, 11, 'F', 'ECONOMY', 7, 6, TRUE,  FALSE)
ON CONFLICT (aircraft_type_id, seat_row, seat_col) DO UPDATE
    SET seat_class = EXCLUDED.seat_class,
        display_row = EXCLUDED.display_row,
        display_col = EXCLUDED.display_col,
        is_window   = EXCLUDED.is_window,
        is_aisle    = EXCLUDED.is_aisle;

-- Ensure sequence matches max ID
SELECT setval('seat_templates_seat_template_id_seq', GREATEST((SELECT MAX(seat_template_id) FROM seat_templates), 1));

-- 4. Flights (4 Flights JNB to CPT with different times and base prices)
-- Using CTE to lookup airport IDs
WITH jnb AS (SELECT airport_id FROM airports WHERE iata_code = 'JNB' LIMIT 1),
     cpt AS (SELECT airport_id FROM airports WHERE iata_code = 'CPT' LIMIT 1)
INSERT INTO flights (flight_id, flight_number, aircraft_type_id, origin_airport_id, dest_airport_id, departure_time, arrival_time, base_price, status)
VALUES
    (1, 'GRP 204', 1, (SELECT airport_id FROM jnb), (SELECT airport_id FROM cpt), '2026-11-02 06:30:00+02', '2026-11-02 08:40:00+02', 1200.00, 'SCHEDULED'),
    (2, 'GRP 310', 1, (SELECT airport_id FROM jnb), (SELECT airport_id FROM cpt), '2026-11-02 09:15:00+02', '2026-11-02 11:25:00+02', 1450.00, 'SCHEDULED'),
    (3, 'GRP 418', 1, (SELECT airport_id FROM jnb), (SELECT airport_id FROM cpt), '2026-11-02 13:00:00+02', '2026-11-02 15:10:00+02', 1180.00, 'SCHEDULED'),
    (4, 'GRP 522', 1, (SELECT airport_id FROM jnb), (SELECT airport_id FROM cpt), '2026-11-02 17:45:00+02', '2026-11-02 20:00:00+02', 1350.00, 'SCHEDULED')
ON CONFLICT (flight_id) DO UPDATE
    SET flight_number     = EXCLUDED.flight_number,
        aircraft_type_id  = EXCLUDED.aircraft_type_id,
        origin_airport_id = EXCLUDED.origin_airport_id,
        dest_airport_id   = EXCLUDED.dest_airport_id,
        departure_time    = EXCLUDED.departure_time,
        arrival_time      = EXCLUDED.arrival_time,
        base_price        = EXCLUDED.base_price,
        status            = EXCLUDED.status;

-- Ensure sequence matches max ID
SELECT setval('flights_flight_id_seq', GREATEST((SELECT MAX(flight_id) FROM flights), 1));

-- 5. Flight Seats: Instantiated for each flight (~1/3 marked BOOKED/TAKEN)
-- Total seats = 44 per flight. 15 seats booked = 34.1% (~1/3).

-- Flight 1 (GRP 204) — matches reference 04_seat_map layout (7E is AVAILABLE)
INSERT INTO flight_seats (flight_id, seat_template_id, status)
SELECT
    1,
    st.seat_template_id,
    CASE
        WHEN (st.seat_row = 1 AND st.seat_col = 'C') THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 2 AND st.seat_col IN ('D', 'F')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 6 AND st.seat_col IN ('A', 'B')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 7 AND st.seat_col IN ('C', 'D', 'F')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 8 AND st.seat_col = 'A') THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 9 AND st.seat_col IN ('B', 'E')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 10 AND st.seat_col IN ('C', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 11 AND st.seat_col IN ('A', 'F')) THEN 'BOOKED'::seat_status
        ELSE 'AVAILABLE'::seat_status
    END
FROM seat_templates st
WHERE st.aircraft_type_id = 1
ON CONFLICT (flight_id, seat_template_id) DO UPDATE
    SET status = EXCLUDED.status;

-- Flight 2 (GRP 310) — 15 booked seats
INSERT INTO flight_seats (flight_id, seat_template_id, status)
SELECT
    2,
    st.seat_template_id,
    CASE
        WHEN (st.seat_row = 1 AND st.seat_col IN ('A', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 2 AND st.seat_col = 'B') THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 6 AND st.seat_col IN ('C', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 7 AND st.seat_col IN ('A', 'B')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 8 AND st.seat_col IN ('C', 'E')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 9 AND st.seat_col IN ('A', 'F')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 10 AND st.seat_col IN ('B', 'E')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 11 AND st.seat_col IN ('C', 'D')) THEN 'BOOKED'::seat_status
        ELSE 'AVAILABLE'::seat_status
    END
FROM seat_templates st
WHERE st.aircraft_type_id = 1
ON CONFLICT (flight_id, seat_template_id) DO UPDATE
    SET status = EXCLUDED.status;

-- Flight 3 (GRP 418) — 15 booked seats
INSERT INTO flight_seats (flight_id, seat_template_id, status)
SELECT
    3,
    st.seat_template_id,
    CASE
        WHEN (st.seat_row = 1 AND st.seat_col = 'C') THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 2 AND st.seat_col IN ('A', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 6 AND st.seat_col IN ('E', 'F')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 7 AND st.seat_col IN ('B', 'E')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 8 AND st.seat_col IN ('B', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 9 AND st.seat_col IN ('C', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 10 AND st.seat_col IN ('A', 'F')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 11 AND st.seat_col IN ('B', 'E')) THEN 'BOOKED'::seat_status
        ELSE 'AVAILABLE'::seat_status
    END
FROM seat_templates st
WHERE st.aircraft_type_id = 1
ON CONFLICT (flight_id, seat_template_id) DO UPDATE
    SET status = EXCLUDED.status;

-- Flight 4 (GRP 522) — 15 booked seats
INSERT INTO flight_seats (flight_id, seat_template_id, status)
SELECT
    4,
    st.seat_template_id,
    CASE
        WHEN (st.seat_row = 1 AND st.seat_col IN ('A', 'B')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 2 AND st.seat_col = 'C') THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 6 AND st.seat_col IN ('B', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 7 AND st.seat_col IN ('C', 'F')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 8 AND st.seat_col IN ('A', 'E')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 9 AND st.seat_col IN ('B', 'D')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 10 AND st.seat_col IN ('C', 'E')) THEN 'BOOKED'::seat_status
        WHEN (st.seat_row = 11 AND st.seat_col IN ('A', 'D')) THEN 'BOOKED'::seat_status
        ELSE 'AVAILABLE'::seat_status
    END
FROM seat_templates st
WHERE st.aircraft_type_id = 1
ON CONFLICT (flight_id, seat_template_id) DO UPDATE
    SET status = EXCLUDED.status;

-- Ensure sequence matches max ID
SELECT setval('flight_seats_flight_seat_id_seq', GREATEST((SELECT MAX(flight_seat_id) FROM flight_seats), 1));

COMMIT;
