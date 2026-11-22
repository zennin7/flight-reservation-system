-- P2 Migration: Seat hold ownership, payment idempotency, refund tracking, and cancellable booking seats.
-- Safe to re-run (idempotent).

ALTER TABLE flight_seats
    ADD COLUMN IF NOT EXISTS held_by VARCHAR(120);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_idempotency_key
    ON payments (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refund_ref VARCHAR(60);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMPTZ;

ALTER TABLE booking_seats
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE booking_seats
    DROP CONSTRAINT IF EXISTS booking_seats_flight_seat_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_booking_seats_active_seat
    ON booking_seats (flight_seat_id)
    WHERE is_active = TRUE;
