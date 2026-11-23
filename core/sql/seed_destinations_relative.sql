-- seed_destinations_relative.sql
-- Rolling 14-day schedule of Sky Royal (SR) domestic and international destination flights
-- on the existing A320 type, generated from the South African calendar date.
-- Safe to run repeatedly (idempotent); no DELETE, TRUNCATE or DROP.
BEGIN;

SELECT setval(pg_get_serial_sequence('airports', 'airport_id'),
              (SELECT COALESCE(MAX(airport_id), 1) FROM airports));
SELECT setval(pg_get_serial_sequence('flights', 'flight_id'),
              (SELECT COALESCE(MAX(flight_id), 1) FROM flights));
SELECT setval(pg_get_serial_sequence('flight_seats', 'flight_seat_id'),
              (SELECT COALESCE(MAX(flight_seat_id), 1) FROM flight_seats));

-- 1. Destination airports
INSERT INTO airports (iata_code, name, city, country, timezone) VALUES
    ('PLZ', 'Chief Dawid Stuurman International Airport', 'Gqeberha', 'South Africa', 'Africa/Johannesburg'),
    ('GRJ', 'George Airport', 'George', 'South Africa', 'Africa/Johannesburg'),
    ('DXB', 'Dubai International Airport', 'Dubai', 'United Arab Emirates', 'Asia/Dubai'),
    ('FCO', 'Leonardo da Vinci-Fiumicino Airport', 'Rome', 'Italy', 'Europe/Rome'),
    ('ZRH', 'Zurich Airport', 'Swiss Alps', 'Switzerland', 'Europe/Zurich'),
    ('MXP', 'Milan Malpensa Airport', 'Lake Como', 'Italy', 'Europe/Rome'),
    ('JTR', 'Santorini International Airport', 'Santorini', 'Greece', 'Europe/Athens'),
    ('NCE', 'Nice Cote d''Azur Airport', 'Monaco', 'France', 'Europe/Paris'),
    ('PSA', 'Pisa International Airport', 'Cinque Terre', 'Italy', 'Europe/Rome'),
    ('GVA', 'Geneva Airport', 'Valais', 'Switzerland', 'Europe/Zurich')
ON CONFLICT (iata_code) DO NOTHING;

-- 2. Destination flight templates
CREATE TEMP TABLE dest_seed_template (
    flight_number text, orig char(3), dest char(3),
    dep_time time, dur interval, base_price numeric(10,2)
) ON COMMIT DROP;

INSERT INTO dest_seed_template VALUES
    ('SR 720','JNB','PLZ',TIME '08:15',INTERVAL '1 hour 40 minutes',1140.00),
    ('SR 721','PLZ','JNB',TIME '10:45',INTERVAL '1 hour 40 minutes',1140.00),
    ('SR 730','JNB','GRJ',TIME '09:30',INTERVAL '1 hour 55 minutes',1290.00),
    ('SR 731','GRJ','JNB',TIME '12:15',INTERVAL '1 hour 55 minutes',1290.00),
    ('SR 801','JNB','DXB',TIME '19:40',INTERVAL '8 hours 10 minutes',8450.00),
    ('SR 802','CPT','DXB',TIME '20:15',INTERVAL '9 hours 20 minutes',8990.00),
    ('SR 811','JNB','FCO',TIME '20:50',INTERVAL '10 hours 15 minutes',9750.00),
    ('SR 821','JNB','ZRH',TIME '21:10',INTERVAL '10 hours 35 minutes',10200.00),
    ('SR 831','JNB','MXP',TIME '18:40',INTERVAL '10 hours 40 minutes',9450.00),
    ('SR 841','JNB','JTR',TIME '19:15',INTERVAL '9 hours 50 minutes',10500.00),
    ('SR 851','JNB','NCE',TIME '21:30',INTERVAL '10 hours 25 minutes',9800.00),
    ('SR 861','JNB','PSA',TIME '20:05',INTERVAL '10 hours 30 minutes',9350.00),
    ('SR 871','JNB','GVA',TIME '21:45',INTERVAL '10 hours 45 minutes',10150.00);

CREATE TEMP TABLE dest_seed_days ON COMMIT DROP AS
SELECT ((NOW() AT TIME ZONE 'Africa/Johannesburg')::date + i) AS op_date
FROM generate_series(1, 14) AS i;

-- 3. Flights
INSERT INTO flights (flight_number, aircraft_type_id, origin_airport_id, dest_airport_id,
                     departure_time, arrival_time, base_price, status)
SELECT s.flight_number, 1, o.airport_id, a.airport_id,
       ((d.op_date + s.dep_time) AT TIME ZONE o.timezone),
       ((d.op_date + s.dep_time) AT TIME ZONE o.timezone) + s.dur,
       s.base_price, 'SCHEDULED'::flight_status
FROM dest_seed_days d
CROSS JOIN dest_seed_template s
JOIN airports o ON o.iata_code = s.orig
JOIN airports a ON a.iata_code = s.dest
WHERE NOT EXISTS (
    SELECT 1 FROM flights f
    WHERE f.flight_number = s.flight_number
      AND f.departure_time = ((d.op_date + s.dep_time) AT TIME ZONE o.timezone)
);

-- 4. Seats for newly seeded flights
INSERT INTO flight_seats (flight_id, seat_template_id, status)
SELECT f.flight_id, st.seat_template_id, 'AVAILABLE'::seat_status
FROM flights f
JOIN seat_templates st ON st.aircraft_type_id = f.aircraft_type_id
WHERE f.flight_number LIKE 'SR %'
  AND NOT EXISTS (SELECT 1 FROM flight_seats x WHERE x.flight_id = f.flight_id)
ON CONFLICT (flight_id, seat_template_id) DO NOTHING;

COMMIT;
