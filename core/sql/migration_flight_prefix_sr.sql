-- Idempotent migration: replace legacy ALT flight prefix with Sky Royal prefix SR.
UPDATE flights f
SET flight_number = regexp_replace(f.flight_number, '^ALT ', 'SR ')
WHERE f.flight_number LIKE 'ALT %'
  AND NOT EXISTS (
      SELECT 1
      FROM flights existing
      WHERE existing.flight_number = regexp_replace(f.flight_number, '^ALT ', 'SR ')
        AND existing.departure_time = f.departure_time
  );
