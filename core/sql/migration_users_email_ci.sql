-- Idempotent case-insensitive unique email index on users.
-- Fails loudly with conflicting LOWER(email) groups without deleting any rows.

DO $$
DECLARE
    v_conflicts TEXT;
BEGIN
    SELECT string_agg(
               format('%s (count=%s, ids=[%s])', norm_email, cnt, ids),
               '; '
           )
      INTO v_conflicts
      FROM (
          SELECT LOWER(email) AS norm_email,
                 COUNT(*)     AS cnt,
                 string_agg(user_id::TEXT || ':' || username || '<' || email || '>', ', ' ORDER BY user_id) AS ids
            FROM users
           WHERE email IS NOT NULL
           GROUP BY LOWER(email)
          HAVING COUNT(*) > 1
      ) dupes;

    IF v_conflicts IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot create case-insensitive unique index ux_users_email_lower: conflicting LOWER(email) groups found (no rows were deleted): %', v_conflicts;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (LOWER(email));
