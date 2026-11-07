BEGIN;
ALTER TABLE airports ADD COLUMN timezone VARCHAR(50);
UPDATE airports SET timezone = 'Africa/Johannesburg' WHERE iata_code IN ('JNB','CPT');
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM airports WHERE timezone IS NULL) THEN
    RAISE EXCEPTION 'airports without timezone: add them to this migration';
  END IF;
END $$;
ALTER TABLE airports ALTER COLUMN timezone SET NOT NULL;
COMMIT;
