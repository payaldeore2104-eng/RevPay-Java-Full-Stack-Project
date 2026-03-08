
SET DEFINE OFF;

-- ============================================================
-- STEP 1: Update TRANSFER_MONEY procedure (fixes 'You sent $')
-- ============================================================
CREATE OR REPLACE PROCEDURE transfer_money(
    p_sender_id   IN  NUMBER,
    p_receiver_id IN  NUMBER,
    p_amount      IN  NUMBER,
    p_description IN  VARCHAR2,
    p_out_tx_id   OUT NUMBER
) AS
    v_balance NUMBER;
BEGIN
    SELECT balance INTO v_balance FROM wallet WHERE user_id = p_sender_id FOR UPDATE;

    IF v_balance < p_amount THEN
        RAISE_APPLICATION_ERROR(-20001, 'Insufficient wallet balance');
    END IF;

    UPDATE wallet SET balance = balance - p_amount, updated_at = SYSTIMESTAMP
     WHERE user_id = p_sender_id;

    UPDATE wallet SET balance = balance + p_amount, updated_at = SYSTIMESTAMP
     WHERE user_id = p_receiver_id;

    IF SQL%ROWCOUNT = 0 THEN
        INSERT INTO wallet(user_id, balance, currency, updated_at)
        VALUES(p_receiver_id, p_amount, 'INR', SYSTIMESTAMP);
    END IF;

    INSERT INTO transactions(sender_id, receiver_id, amount, transaction_type, description, status, created_at)
    VALUES(p_sender_id, p_receiver_id, p_amount, 'TRANSFER', p_description, 'COMPLETED', SYSTIMESTAMP)
    RETURNING id INTO p_out_tx_id;

    INSERT INTO notifications(user_id, message) VALUES(p_sender_id,
        'You sent ₹' || p_amount || ' - ' || NVL(p_description, 'Transfer'));
    INSERT INTO notifications(user_id, message) VALUES(p_receiver_id,
        'You received ₹' || p_amount || ' - ' || NVL(p_description, 'Transfer'));

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END transfer_money;
/

-- ============================================================
-- STEP 2: Update CREATE_REQUEST procedure (fixes 'Money request of $')
-- ============================================================
CREATE OR REPLACE PROCEDURE create_request(
    p_requester_id  IN  NUMBER,
    p_requestee_id  IN  NUMBER,
    p_amount        IN  NUMBER,
    p_description   IN  VARCHAR2,
    p_out_req_id    OUT NUMBER
) AS
BEGIN
    INSERT INTO money_requests(requester_id, requestee_id, amount, description, status)
    VALUES(p_requester_id, p_requestee_id, p_amount, p_description, 'PENDING')
    RETURNING id INTO p_out_req_id;

    INSERT INTO notifications(user_id, message) VALUES(p_requestee_id,
        'Money request of ₹' || p_amount || ' from user #' || p_requester_id);

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END create_request;
/

-- ============================================================
-- STEP 3: Fix existing wallet rows with old USD currency
-- ============================================================
UPDATE wallet SET currency = 'INR' WHERE currency = 'USD';
COMMIT;

-- ============================================================
-- STEP 4: Fix ALL existing notification messages with $ symbol
-- This updates every historical notification in the database
-- ============================================================
UPDATE notifications SET message = REPLACE(message, '$', '₹')
WHERE message LIKE '%$%';
COMMIT;

-- ============================================================
-- STEP 5: Verify
-- ============================================================
SELECT COUNT(*) AS "Remaining $ in notifications"
FROM notifications WHERE message LIKE '%$%';

SELECT COUNT(*) AS "Wallet rows still USD"
FROM wallet WHERE currency = 'USD';

SELECT 'INR patch complete!' AS status FROM dual;

