
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (transaction_pin VARCHAR2(255))';
EXCEPTION WHEN OTHERS THEN NULL; -- ignore if already exists
END;
/

-- ============================================================
-- REGISTER_USER
-- Called by: UserServiceImpl.registerUser()
-- ============================================================
CREATE OR REPLACE PROCEDURE register_user(
    p_full_name     IN  VARCHAR2,
    p_email         IN  VARCHAR2,
    p_phone         IN  VARCHAR2,
    p_password_hash IN  VARCHAR2,
    p_role_name     IN  VARCHAR2,
    p_business_name IN  VARCHAR2,
    p_business_type IN  VARCHAR2,
    p_tax_id        IN  VARCHAR2,
    p_address       IN  VARCHAR2,
    p_out_user_id   OUT NUMBER
) AS
    v_role VARCHAR2(30);
BEGIN
    -- Map role name to ROLE_ prefix
    v_role := CASE
        WHEN UPPER(p_role_name) = 'BUSINESS'  THEN 'ROLE_BUSINESS'
        WHEN UPPER(p_role_name) = 'ADMIN'     THEN 'ROLE_ADMIN'
        ELSE 'ROLE_PERSONAL'
    END;

    -- Insert user
    INSERT INTO users(full_name, email, phone, password_hash, role, is_active, login_attempts, created_at)
    VALUES(p_full_name, p_email, p_phone, p_password_hash, v_role, 1, 0, SYSTIMESTAMP)
    RETURNING id INTO p_out_user_id;

    -- Auto-create wallet with $0 balance
    INSERT INTO wallet(user_id, balance, currency, updated_at)
    VALUES(p_out_user_id, 0.00, 'INR', SYSTIMESTAMP);

    -- Welcome notification
    INSERT INTO notifications(user_id, message)
    VALUES(p_out_user_id, 'Welcome to RevPay, ' || p_full_name || '! Your account has been created.');

    COMMIT;
EXCEPTION
    WHEN DUP_VAL_ON_INDEX THEN
        ROLLBACK;
        RAISE_APPLICATION_ERROR(-20002, 'Email or phone already registered');
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END register_user;
/

-- ============================================================
-- VALIDATE_LOGIN
-- Called by: UserServiceImpl.validateLogin()
-- ============================================================
CREATE OR REPLACE PROCEDURE validate_login(
    p_login_id      IN  VARCHAR2,
    p_out_status    OUT VARCHAR2,
    p_out_hash      OUT VARCHAR2,
    p_out_user_id   OUT NUMBER
) AS
    v_attempts  NUMBER;
    v_is_active NUMBER;
BEGIN
    SELECT id, password_hash, login_attempts, is_active
      INTO p_out_user_id, p_out_hash, v_attempts, v_is_active
      FROM users
     WHERE email = p_login_id OR phone = p_login_id
       AND ROWNUM = 1;

    IF v_is_active = 0 OR v_attempts >= 5 THEN
        p_out_status := 'LOCKED';
    ELSE
        p_out_status := 'OK';
    END IF;

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_out_status  := 'NOT_FOUND';
        p_out_hash    := NULL;
        p_out_user_id := NULL;
    WHEN OTHERS THEN
        p_out_status  := 'ERROR';
        p_out_hash    := NULL;
        p_out_user_id := NULL;
END validate_login;
/

-- ============================================================
-- UPDATE_FAILED_ATTEMPTS  (called after wrong password)
-- ============================================================
CREATE OR REPLACE PROCEDURE update_failed_attempts(
    p_user_id   IN  NUMBER,
    p_out_locked OUT NUMBER
) AS
    v_attempts NUMBER;
BEGIN
    UPDATE users
       SET login_attempts = login_attempts + 1
     WHERE id = p_user_id
    RETURNING login_attempts INTO v_attempts;

    IF v_attempts >= 5 THEN
        UPDATE users SET is_active = 0 WHERE id = p_user_id;
        p_out_locked := 1;
    ELSE
        p_out_locked := 0;
    END IF;

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END update_failed_attempts;
/

-- ============================================================
-- RESET_FAILED_ATTEMPTS  (called after successful login)
-- ============================================================
CREATE OR REPLACE PROCEDURE reset_failed_attempts(
    p_user_id IN NUMBER
) AS
BEGIN
    UPDATE users
       SET login_attempts = 0,
           is_active      = 1
     WHERE id = p_user_id;
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END reset_failed_attempts;
/

-- ============================================================
-- RESET_PASSWORD
-- ============================================================
CREATE OR REPLACE PROCEDURE reset_password(
    p_user_id          IN NUMBER,
    p_new_password_hash IN VARCHAR2
) AS
BEGIN
    UPDATE users SET password_hash = p_new_password_hash WHERE id = p_user_id;
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END reset_password;
/

-- ============================================================
-- VALIDATE_PIN
-- ============================================================
CREATE OR REPLACE PROCEDURE validate_pin(
    p_user_id       IN  NUMBER,
    p_out_pin_hash  OUT VARCHAR2
) AS
BEGIN
    SELECT transaction_pin INTO p_out_pin_hash
      FROM users WHERE id = p_user_id;
EXCEPTION
    WHEN NO_DATA_FOUND THEN p_out_pin_hash := NULL;
    WHEN OTHERS THEN p_out_pin_hash := NULL;
END validate_pin;
/

-- ============================================================
-- VERIFY: Show all user-management procedures
-- ============================================================
SELECT object_name, status
  FROM user_objects
 WHERE object_type = 'PROCEDURE'
 ORDER BY object_name;
