-- ============================================================
-- SEED DATA — test user for local Auth testing
-- Password for this user is: Passw0rd!
-- Hash below is a real, verified BCrypt hash of that password.
-- ============================================================

INSERT INTO users (username, password_hash, role, full_name, email, is_active) VALUES
    ('testadmin', '$2b$12$Mxh/5oczFTWNSNOP.yx2JutGuTVdCxyCtDRg00QqZrkCTWl7xai42', 'ADMIN', 'Test Admin', 'testadmin@example.com', TRUE);

