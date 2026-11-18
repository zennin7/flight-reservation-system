-- ============================================================
-- SKY ROYAL FLIGHT RESERVATION SYSTEM — SEED FULL CABIN
-- Aircraft: Airbus A320 (162 seats)
--   - Rows 1-3: First Class 2x2 (12 seats)
--   - Rows 4-28: Economy Class 3x3 (150 seats)
--   - Total: 162 seats per flight for Flights 1 to 4
-- Safe migration: Preserves existing booking_seats references
-- (flight_seat_id 1, 3, 4, 5 and seat_template_id 1 to 8 untouched).
-- ============================================================

BEGIN;

-- 1. Update Aircraft Type 1 to Airbus A320 with 28 rows and 7-col grid width
UPDATE aircraft_types
SET model = 'Airbus A320',
    total_rows = 28,
    grid_width = 7
WHERE aircraft_type_id = 1;

-- 1b. Update templates 1-8 (rows 1-2) to First Class 2x2: A, C, D, F at display_col 0, 2, 4, 6
-- Safe migration from legacy (B, C, D at 1, 3, 4) to airline standard (C, D, F at 2, 4, 6)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM seat_templates 
        WHERE aircraft_type_id = 1 AND seat_row IN (1, 2) AND seat_col = 'B'
    ) THEN
        UPDATE seat_templates
        SET display_col = display_col + 100,
            seat_col = CASE seat_col
                WHEN 'B' THEN 'X'
                WHEN 'C' THEN 'Y'
                WHEN 'D' THEN 'Z'
                ELSE seat_col
            END
        WHERE aircraft_type_id = 1 AND seat_row IN (1, 2) AND seat_col IN ('B', 'C', 'D');

        UPDATE seat_templates
        SET seat_col = CASE seat_col
                WHEN 'X' THEN 'C'
                WHEN 'Y' THEN 'D'
                WHEN 'Z' THEN 'F'
                ELSE seat_col
            END,
            display_col = CASE seat_col
                WHEN 'X' THEN 2
                WHEN 'Y' THEN 4
                WHEN 'Z' THEN 6
                ELSE display_col
            END,
            is_window = CASE seat_col
                WHEN 'Z' THEN TRUE
                ELSE is_window
            END,
            is_aisle = CASE seat_col
                WHEN 'X' THEN TRUE
                WHEN 'Y' THEN TRUE
                WHEN 'Z' THEN FALSE
                ELSE is_aisle
            END
        WHERE aircraft_type_id = 1 AND seat_row IN (1, 2) AND seat_col IN ('X', 'Y', 'Z');
    ELSE
        UPDATE seat_templates
        SET display_col = CASE seat_col
                WHEN 'A' THEN 0
                WHEN 'C' THEN 2
                WHEN 'D' THEN 4
                WHEN 'F' THEN 6
            END,
            is_window = (seat_col IN ('A', 'F')),
            is_aisle  = (seat_col IN ('C', 'D'))
        WHERE aircraft_type_id = 1 AND seat_row IN (1, 2) AND seat_col IN ('A', 'C', 'D', 'F');
    END IF;
END $$;

-- 2. Shift display_row for existing seat_templates (rows 6..11) by +3 to make room for rows 3..5
-- Idempotent check ensures rows already at display_row 5..10 are not shifted multiple times
UPDATE seat_templates
SET display_row = display_row + 100
WHERE aircraft_type_id = 1 AND seat_row >= 6 AND seat_row <= 11 AND display_row < 5;

UPDATE seat_templates
SET display_row = display_row - 97
WHERE aircraft_type_id = 1 AND seat_row >= 6 AND seat_row <= 11 AND display_row >= 100;

-- 3. Insert Row 3 First Class (2x2, 4 seats, display_row 2: A, C, D, F at cols 0, 2, 4, 6)
INSERT INTO seat_templates (aircraft_type_id, seat_row, seat_col, seat_class, display_row, display_col, is_window, is_aisle)
VALUES
    (1, 3, 'A', 'FIRST', 2, 0, TRUE,  FALSE),
    (1, 3, 'C', 'FIRST', 2, 2, FALSE, TRUE),
    (1, 3, 'D', 'FIRST', 2, 4, FALSE, TRUE),
    (1, 3, 'F', 'FIRST', 2, 6, TRUE,  FALSE)
ON CONFLICT (aircraft_type_id, seat_row, seat_col) DO UPDATE
    SET display_row = EXCLUDED.display_row,
        display_col = EXCLUDED.display_col,
        is_window = EXCLUDED.is_window,
        is_aisle = EXCLUDED.is_aisle;

-- 4. Insert Rows 4 and 5 Economy Class (3x3, 12 seats, display_rows 3 and 4)
INSERT INTO seat_templates (aircraft_type_id, seat_row, seat_col, seat_class, display_row, display_col, is_window, is_aisle)
VALUES
    -- Row 4 Economy
    (1, 4, 'A', 'ECONOMY', 3, 0, TRUE,  FALSE),
    (1, 4, 'B', 'ECONOMY', 3, 1, FALSE, FALSE),
    (1, 4, 'C', 'ECONOMY', 3, 2, FALSE, TRUE),
    (1, 4, 'D', 'ECONOMY', 3, 4, FALSE, TRUE),
    (1, 4, 'E', 'ECONOMY', 3, 5, FALSE, FALSE),
    (1, 4, 'F', 'ECONOMY', 3, 6, TRUE,  FALSE),
    -- Row 5 Economy
    (1, 5, 'A', 'ECONOMY', 4, 0, TRUE,  FALSE),
    (1, 5, 'B', 'ECONOMY', 4, 1, FALSE, FALSE),
    (1, 5, 'C', 'ECONOMY', 4, 2, FALSE, TRUE),
    (1, 5, 'D', 'ECONOMY', 4, 4, FALSE, TRUE),
    (1, 5, 'E', 'ECONOMY', 4, 5, FALSE, FALSE),
    (1, 5, 'F', 'ECONOMY', 4, 6, TRUE,  FALSE)
ON CONFLICT (aircraft_type_id, seat_row, seat_col) DO NOTHING;

-- 5. Insert Rows 12 to 28 Economy Class (3x3, 102 seats, display_rows 11 to 27)
INSERT INTO seat_templates (aircraft_type_id, seat_row, seat_col, seat_class, display_row, display_col, is_window, is_aisle)
VALUES
    -- Row 12 Economy
    (1, 12, 'A', 'ECONOMY', 11, 0, TRUE,  FALSE),
    (1, 12, 'B', 'ECONOMY', 11, 1, FALSE, FALSE),
    (1, 12, 'C', 'ECONOMY', 11, 2, FALSE, TRUE),
    (1, 12, 'D', 'ECONOMY', 11, 4, FALSE, TRUE),
    (1, 12, 'E', 'ECONOMY', 11, 5, FALSE, FALSE),
    (1, 12, 'F', 'ECONOMY', 11, 6, TRUE,  FALSE),
    -- Row 13 Economy
    (1, 13, 'A', 'ECONOMY', 12, 0, TRUE,  FALSE),
    (1, 13, 'B', 'ECONOMY', 12, 1, FALSE, FALSE),
    (1, 13, 'C', 'ECONOMY', 12, 2, FALSE, TRUE),
    (1, 13, 'D', 'ECONOMY', 12, 4, FALSE, TRUE),
    (1, 13, 'E', 'ECONOMY', 12, 5, FALSE, FALSE),
    (1, 13, 'F', 'ECONOMY', 12, 6, TRUE,  FALSE),
    -- Row 14 Economy
    (1, 14, 'A', 'ECONOMY', 13, 0, TRUE,  FALSE),
    (1, 14, 'B', 'ECONOMY', 13, 1, FALSE, FALSE),
    (1, 14, 'C', 'ECONOMY', 13, 2, FALSE, TRUE),
    (1, 14, 'D', 'ECONOMY', 13, 4, FALSE, TRUE),
    (1, 14, 'E', 'ECONOMY', 13, 5, FALSE, FALSE),
    (1, 14, 'F', 'ECONOMY', 13, 6, TRUE,  FALSE),
    -- Row 15 Economy
    (1, 15, 'A', 'ECONOMY', 14, 0, TRUE,  FALSE),
    (1, 15, 'B', 'ECONOMY', 14, 1, FALSE, FALSE),
    (1, 15, 'C', 'ECONOMY', 14, 2, FALSE, TRUE),
    (1, 15, 'D', 'ECONOMY', 14, 4, FALSE, TRUE),
    (1, 15, 'E', 'ECONOMY', 14, 5, FALSE, FALSE),
    (1, 15, 'F', 'ECONOMY', 14, 6, TRUE,  FALSE),
    -- Row 16 Economy
    (1, 16, 'A', 'ECONOMY', 15, 0, TRUE,  FALSE),
    (1, 16, 'B', 'ECONOMY', 15, 1, FALSE, FALSE),
    (1, 16, 'C', 'ECONOMY', 15, 2, FALSE, TRUE),
    (1, 16, 'D', 'ECONOMY', 15, 4, FALSE, TRUE),
    (1, 16, 'E', 'ECONOMY', 15, 5, FALSE, FALSE),
    (1, 16, 'F', 'ECONOMY', 15, 6, TRUE,  FALSE),
    -- Row 17 Economy
    (1, 17, 'A', 'ECONOMY', 16, 0, TRUE,  FALSE),
    (1, 17, 'B', 'ECONOMY', 16, 1, FALSE, FALSE),
    (1, 17, 'C', 'ECONOMY', 16, 2, FALSE, TRUE),
    (1, 17, 'D', 'ECONOMY', 16, 4, FALSE, TRUE),
    (1, 17, 'E', 'ECONOMY', 16, 5, FALSE, FALSE),
    (1, 17, 'F', 'ECONOMY', 16, 6, TRUE,  FALSE),
    -- Row 18 Economy
    (1, 18, 'A', 'ECONOMY', 17, 0, TRUE,  FALSE),
    (1, 18, 'B', 'ECONOMY', 17, 1, FALSE, FALSE),
    (1, 18, 'C', 'ECONOMY', 17, 2, FALSE, TRUE),
    (1, 18, 'D', 'ECONOMY', 17, 4, FALSE, TRUE),
    (1, 18, 'E', 'ECONOMY', 17, 5, FALSE, FALSE),
    (1, 18, 'F', 'ECONOMY', 17, 6, TRUE,  FALSE),
    -- Row 19 Economy
    (1, 19, 'A', 'ECONOMY', 18, 0, TRUE,  FALSE),
    (1, 19, 'B', 'ECONOMY', 18, 1, FALSE, FALSE),
    (1, 19, 'C', 'ECONOMY', 18, 2, FALSE, TRUE),
    (1, 19, 'D', 'ECONOMY', 18, 4, FALSE, TRUE),
    (1, 19, 'E', 'ECONOMY', 18, 5, FALSE, FALSE),
    (1, 19, 'F', 'ECONOMY', 18, 6, TRUE,  FALSE),
    -- Row 20 Economy
    (1, 20, 'A', 'ECONOMY', 19, 0, TRUE,  FALSE),
    (1, 20, 'B', 'ECONOMY', 19, 1, FALSE, FALSE),
    (1, 20, 'C', 'ECONOMY', 19, 2, FALSE, TRUE),
    (1, 20, 'D', 'ECONOMY', 19, 4, FALSE, TRUE),
    (1, 20, 'E', 'ECONOMY', 19, 5, FALSE, FALSE),
    (1, 20, 'F', 'ECONOMY', 19, 6, TRUE,  FALSE),
    -- Row 21 Economy
    (1, 21, 'A', 'ECONOMY', 20, 0, TRUE,  FALSE),
    (1, 21, 'B', 'ECONOMY', 20, 1, FALSE, FALSE),
    (1, 21, 'C', 'ECONOMY', 20, 2, FALSE, TRUE),
    (1, 21, 'D', 'ECONOMY', 20, 4, FALSE, TRUE),
    (1, 21, 'E', 'ECONOMY', 20, 5, FALSE, FALSE),
    (1, 21, 'F', 'ECONOMY', 20, 6, TRUE,  FALSE),
    -- Row 22 Economy
    (1, 22, 'A', 'ECONOMY', 21, 0, TRUE,  FALSE),
    (1, 22, 'B', 'ECONOMY', 21, 1, FALSE, FALSE),
    (1, 22, 'C', 'ECONOMY', 21, 2, FALSE, TRUE),
    (1, 22, 'D', 'ECONOMY', 21, 4, FALSE, TRUE),
    (1, 22, 'E', 'ECONOMY', 21, 5, FALSE, FALSE),
    (1, 22, 'F', 'ECONOMY', 21, 6, TRUE,  FALSE),
    -- Row 23 Economy
    (1, 23, 'A', 'ECONOMY', 22, 0, TRUE,  FALSE),
    (1, 23, 'B', 'ECONOMY', 22, 1, FALSE, FALSE),
    (1, 23, 'C', 'ECONOMY', 22, 2, FALSE, TRUE),
    (1, 23, 'D', 'ECONOMY', 22, 4, FALSE, TRUE),
    (1, 23, 'E', 'ECONOMY', 22, 5, FALSE, FALSE),
    (1, 23, 'F', 'ECONOMY', 22, 6, TRUE,  FALSE),
    -- Row 24 Economy
    (1, 24, 'A', 'ECONOMY', 23, 0, TRUE,  FALSE),
    (1, 24, 'B', 'ECONOMY', 23, 1, FALSE, FALSE),
    (1, 24, 'C', 'ECONOMY', 23, 2, FALSE, TRUE),
    (1, 24, 'D', 'ECONOMY', 23, 4, FALSE, TRUE),
    (1, 24, 'E', 'ECONOMY', 23, 5, FALSE, FALSE),
    (1, 24, 'F', 'ECONOMY', 23, 6, TRUE,  FALSE),
    -- Row 25 Economy
    (1, 25, 'A', 'ECONOMY', 24, 0, TRUE,  FALSE),
    (1, 25, 'B', 'ECONOMY', 24, 1, FALSE, FALSE),
    (1, 25, 'C', 'ECONOMY', 24, 2, FALSE, TRUE),
    (1, 25, 'D', 'ECONOMY', 24, 4, FALSE, TRUE),
    (1, 25, 'E', 'ECONOMY', 24, 5, FALSE, FALSE),
    (1, 25, 'F', 'ECONOMY', 24, 6, TRUE,  FALSE),
    -- Row 26 Economy
    (1, 26, 'A', 'ECONOMY', 25, 0, TRUE,  FALSE),
    (1, 26, 'B', 'ECONOMY', 25, 1, FALSE, FALSE),
    (1, 26, 'C', 'ECONOMY', 25, 2, FALSE, TRUE),
    (1, 26, 'D', 'ECONOMY', 25, 4, FALSE, TRUE),
    (1, 26, 'E', 'ECONOMY', 25, 5, FALSE, FALSE),
    (1, 26, 'F', 'ECONOMY', 25, 6, TRUE,  FALSE),
    -- Row 27 Economy
    (1, 27, 'A', 'ECONOMY', 26, 0, TRUE,  FALSE),
    (1, 27, 'B', 'ECONOMY', 26, 1, FALSE, FALSE),
    (1, 27, 'C', 'ECONOMY', 26, 2, FALSE, TRUE),
    (1, 27, 'D', 'ECONOMY', 26, 4, FALSE, TRUE),
    (1, 27, 'E', 'ECONOMY', 26, 5, FALSE, FALSE),
    (1, 27, 'F', 'ECONOMY', 26, 6, TRUE,  FALSE),
    -- Row 28 Economy
    (1, 28, 'A', 'ECONOMY', 27, 0, TRUE,  FALSE),
    (1, 28, 'B', 'ECONOMY', 27, 1, FALSE, FALSE),
    (1, 28, 'C', 'ECONOMY', 27, 2, FALSE, TRUE),
    (1, 28, 'D', 'ECONOMY', 27, 4, FALSE, TRUE),
    (1, 28, 'E', 'ECONOMY', 27, 5, FALSE, FALSE),
    (1, 28, 'F', 'ECONOMY', 27, 6, TRUE,  FALSE)
ON CONFLICT (aircraft_type_id, seat_row, seat_col) DO NOTHING;

-- 6. Synchronize seat_templates sequence
SELECT setval('seat_templates_seat_template_id_seq', (SELECT MAX(seat_template_id) FROM seat_templates));

-- 7. Populate flight_seats for Flights 1, 2, 3, 4 (only missing seats inserted)
-- Existing flight_seats (including booked seats 1, 3, 4, 5) are untouched due to ON CONFLICT DO NOTHING
INSERT INTO flight_seats (flight_id, seat_template_id, status)
SELECT f.flight_id, st.seat_template_id, 'AVAILABLE'
FROM flights f
CROSS JOIN seat_templates st
WHERE f.flight_id IN (1, 2, 3, 4)
  AND st.aircraft_type_id = 1
ON CONFLICT (flight_id, seat_template_id) DO NOTHING;

-- 8. Synchronize flight_seats sequence
SELECT setval('flight_seats_flight_seat_id_seq', (SELECT MAX(flight_seat_id) FROM flight_seats));

COMMIT;
