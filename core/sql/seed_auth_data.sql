-- ============================================================
-- SEED DATA — auth users for local login & testing
-- Password for both accounts is: Passw0rd!
-- Hashes below are real, verified $2a$12$ BCrypt hashes.
-- ============================================================

INSERT INTO users (username, password_hash, role, full_name, email, is_active) VALUES
    ('testadmin', '$2a$12$PbLU1N1783HPCUauaurDb.dhCCkT2YhRtGe5iFruRMvWXCUX16GxG', 'ADMIN', 'Test Admin', 'testadmin@example.com', TRUE),
    ('customer', '$2a$12$wlq6HDixf4yVSCGKvxkjP.a9g1hpAErPgaUXUIIuovvqbFIV7mm8a', 'CUSTOMER', 'Demo Customer', 'customer@example.com', TRUE)
ON CONFLICT (username) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    is_active = TRUE;
