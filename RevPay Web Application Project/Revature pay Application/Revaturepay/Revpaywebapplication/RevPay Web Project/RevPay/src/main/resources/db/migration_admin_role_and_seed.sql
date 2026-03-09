-- ============================================================
-- RevPay Admin Module: Role column (safe) + Admin seed (safe)
-- Oracle SQL (idempotent)
-- ============================================================

-- 1) Ensure ROLE column exists (ROLE_* scheme)
DECLARE
    v_col_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_col_count
      FROM user_tab_columns
     WHERE table_name = 'USERS'
       AND column_name = 'ROLE';

    IF v_col_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE users ADD (role VARCHAR2(20) DEFAULT ''ROLE_PERSONAL'' NOT NULL)';
    END IF;

    -- Backfill any null roles defensively
    BEGIN
        EXECUTE IMMEDIATE 'UPDATE users SET role = ''ROLE_PERSONAL'' WHERE role IS NULL';
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;
END;
/

-- 2) Seed admin account if missing
DECLARE
    v_exists NUMBER := 0;
    v_user_id NUMBER := NULL;
BEGIN
    SELECT COUNT(*)
      INTO v_exists
      FROM users
     WHERE LOWER(email) = 'admin@revpay.com'
        OR phone = '9000000000';

    IF v_exists = 0 THEN
        -- password = admin123 (bcrypt hash)
        INSERT INTO users(full_name, email, phone, password_hash, role, is_active, login_attempts, created_at)
        VALUES('Admin User', 'admin@revpay.com', '9000000000',
               '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
               'ROLE_ADMIN', 1, 0, SYSTIMESTAMP)
        RETURNING id INTO v_user_id;
    ELSE
        BEGIN
            SELECT id INTO v_user_id FROM users WHERE LOWER(email) = 'admin@revpay.com' AND ROWNUM = 1;
        EXCEPTION
            WHEN OTHERS THEN v_user_id := NULL;
        END;
    END IF;

    IF v_user_id IS NOT NULL THEN
        -- Create wallet if missing
        DECLARE v_wallet_cnt NUMBER := 0;
        BEGIN
            SELECT COUNT(*) INTO v_wallet_cnt FROM wallet WHERE user_id = v_user_id;
            IF v_wallet_cnt = 0 THEN
                INSERT INTO wallet(user_id, balance, currency, updated_at)
                VALUES(v_user_id, 10000.00, 'INR', SYSTIMESTAMP);
            END IF;
        EXCEPTION
            WHEN OTHERS THEN NULL;
        END;
    END IF;

    COMMIT;
END;
/

