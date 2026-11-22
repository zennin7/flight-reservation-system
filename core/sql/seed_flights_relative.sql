-- seed_flights_relative.sql
-- Rolling 14-day schedule of SR flights on the existing A320 type, generated from the
-- South African calendar date. Safe to run repeatedly (idempotent); run it again on any
-- later day to extend the window. Fresh-DB order: schema.sql, seed_flights_data.sql,
-- seed_full_cabin.sql, seed_flights_relative.sql. No DELETE, TRUNCATE or DROP.
BEGIN;

-- 0. Earlier seeds inserted explicit IDs; move the sequences past them BEFORE inserting.
SELECT setval(pg_get_serial_sequence('flights', 'flight_id'),
              (SELECT COALESCE(MAX(flight_id), 1) FROM flights));
SELECT setval(pg_get_serial_sequence('flight_seats', 'flight_seat_id'),
              (SELECT COALESCE(MAX(flight_seat_id), 1) FROM flight_seats));

-- 1. Airport DUR
INSERT INTO airports (iata_code, name, city, country, timezone)
VALUES ('DUR', 'King Shaka International Airport', 'Durban', 'South Africa', 'Africa/Johannesburg')
ON CONFLICT (iata_code) DO NOTHING;

-- 2. Template and days (temp tables so the assertions below can reuse them)
CREATE TEMP TABLE seed_template (
    flight_number text, orig char(3), dest char(3),
    dep_time time, dur interval, base_price numeric(10,2)
) ON COMMIT DROP;

INSERT INTO seed_template VALUES
    ('SR 204','JNB','CPT',TIME '06:30',INTERVAL '2 hours 10 minutes',1200.00),
    ('SR 310','JNB','CPT',TIME '09:15',INTERVAL '2 hours 10 minutes',1450.00),
    ('SR 418','JNB','CPT',TIME '13:00',INTERVAL '2 hours 10 minutes',1180.00),
    ('SR 522','JNB','CPT',TIME '17:45',INTERVAL '2 hours 15 minutes',1350.00),
    ('SR 205','CPT','JNB',TIME '06:00',INTERVAL '2 hours',1200.00),
    ('SR 311','CPT','JNB',TIME '10:00',INTERVAL '2 hours',1450.00),
    ('SR 419','CPT','JNB',TIME '14:00',INTERVAL '2 hours',1180.00),
    ('SR 523','CPT','JNB',TIME '18:30',INTERVAL '2 hours',1350.00),
    ('SR 602','JNB','DUR',TIME '07:00',INTERVAL '1 hour 25 minutes',950.00),
    ('SR 604','JNB','DUR',TIME '12:00',INTERVAL '1 hour 25 minutes',1050.00),
    ('SR 606','JNB','DUR',TIME '16:30',INTERVAL '1 hour 25 minutes',980.00),
    ('SR 603','DUR','JNB',TIME '08:45',INTERVAL '1 hour 25 minutes',950.00),
    ('SR 605','DUR','JNB',TIME '14:00',INTERVAL '1 hour 25 minutes',1050.00),
    ('SR 607','DUR','JNB',TIME '18:30',INTERVAL '1 hour 25 minutes',980.00),
    ('SR 702','CPT','DUR',TIME '08:30',INTERVAL '2 hours',1300.00),
    ('SR 703','DUR','CPT',TIME '11:30',INTERVAL '2 hours',1300.00);

CREATE TEMP TABLE seed_days ON COMMIT DROP AS
SELECT ((NOW() AT TIME ZONE 'Africa/Johannesburg')::date + i) AS op_date
FROM generate_series(1, 14) AS i;

-- 3. Flights
INSERT INTO flights (flight_number, aircraft_type_id, origin_airport_id, dest_airport_id,
                     departure_time, arrival_time, base_price, status)
SELECT s.flight_number, 1, o.airport_id, a.airport_id,
       ((d.op_date + s.dep_time) AT TIME ZONE o.timezone),
       ((d.op_date + s.dep_time) AT TIME ZONE o.timezone) + s.dur,
       s.base_price, 'SCHEDULED'::flight_status
FROM seed_days d
CROSS JOIN seed_template s
JOIN airports o ON o.iata_code = s.orig
JOIN airports a ON a.iata_code = s.dest
WHERE NOT EXISTS (
    SELECT 1 FROM flights f
    WHERE f.flight_number = s.flight_number
      AND f.departure_time = ((d.op_date + s.dep_time) AT TIME ZONE o.timezone)
);

-- 4. Seats for every seeded flight that has none yet
INSERT INTO flight_seats (flight_id, seat_template_id, status)
SELECT f.flight_id, st.seat_template_id, 'AVAILABLE'::seat_status
FROM flights f
JOIN seat_templates st ON st.aircraft_type_id = f.aircraft_type_id
WHERE f.flight_number LIKE 'SR %'
  AND NOT EXISTS (SELECT 1 FROM flight_seats x WHERE x.flight_id = f.flight_id)
ON CONFLICT (flight_id, seat_template_id) DO NOTHING;

-- 5. Assertions
DO $$
DECLARE
    missing_flights  INTEGER;
    bad_seat_flights INTEGER;
    bad_times        INTEGER;
    dup_flights      INTEGER;
    total_flights    INTEGER;
    total_seats      BIGINT;
BEGIN
    SELECT COUNT(*) INTO missing_flights
    FROM seed_days d CROSS JOIN seed_template s
    JOIN airports o ON o.iata_code = s.orig
    WHERE NOT EXISTS (
        SELECT 1 FROM flights f
        WHERE f.flight_number = s.flight_number
          AND f.departure_time = ((d.op_date + s.dep_time) AT TIME ZONE o.timezone));
    IF missing_flights <> 0 THEN
        RAISE EXCEPTION 'seed_flights_relative: % scheduled flights are missing (is an airport missing?)', missing_flights;
    END IF;

    SELECT COUNT(*) INTO bad_seat_flights
    FROM flights f
    WHERE f.flight_number LIKE 'SR %'
      AND (SELECT COUNT(*) FROM flight_seats fs WHERE fs.flight_id = f.flight_id)
          <> (SELECT COUNT(*) FROM seat_templates st WHERE st.aircraft_type_id = f.aircraft_type_id);
    IF bad_seat_flights <> 0 THEN
        RAISE EXCEPTION 'seed_flights_relative: % SR flights do not have a full seat set', bad_seat_flights;
    END IF;

    SELECT COUNT(*) INTO bad_times FROM flights
    WHERE flight_number LIKE 'SR %' AND arrival_time <= departure_time;
    IF bad_times <> 0 THEN
        RAISE EXCEPTION 'seed_flights_relative: % SR flights arrive before they depart', bad_times;
    END IF;

    SELECT COUNT(*) INTO dup_flights FROM (
        SELECT flight_number, departure_time FROM flights
        WHERE flight_number LIKE 'SR %'
        GROUP BY flight_number, departure_time HAVING COUNT(*) > 1) x;
    IF dup_flights <> 0 THEN
        RAISE EXCEPTION 'seed_flights_relative: % duplicated SR flights', dup_flights;
    END IF;

    SELECT COUNT(*) INTO total_flights FROM flights WHERE flight_number LIKE 'SR %';
    SELECT COUNT(*) INTO total_seats
    FROM flight_seats fs JOIN flights f ON f.flight_id = fs.flight_id WHERE f.flight_number LIKE 'SR %';
    RAISE NOTICE 'seed_flights_relative OK: % SR flights, % SR seats in total', total_flights, total_seats;
END $$;

COMMIT;
