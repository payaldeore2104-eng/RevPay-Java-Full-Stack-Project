
SET DEFINE OFF;

-- ============================================================
-- STEP 1: Disable all FK constraints to allow truncation
--         regardless of parent/child table order.
-- ============================================================
BEGIN
    FOR c IN (
        SELECT owner, constraint_name, table_name
        FROM   user_constraints
        WHERE  constraint_type = 'R'  -- Referential (FK) constraints only
    ) LOOP
        EXECUTE IMMEDIATE
            'ALTER TABLE ' || c.table_name ||
            ' DISABLE CONSTRAINT ' || c.constraint_name;
    END LOOP;
END;
/

-- ============================================================
-- STEP 2: Delete all data (child tables first, then parents)
-- ============================================================

-- Leaf / child tables (no other tables depend on these)
-- Wrap leaf tables that might not exist in all schema versions
BEGIN EXECUTE IMMEDIATE 'DELETE FROM invoice_items'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'DELETE FROM repayments'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'DELETE FROM user_security_answers'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'DELETE FROM notification_preferences'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'DELETE FROM business_profile'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
/

-- Check if business_verification_docs table exists and delete if so
BEGIN
    EXECUTE IMMEDIATE 'DELETE FROM business_verification_docs';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -942 THEN NULL;  -- Table does not exist, skip
        ELSE RAISE;
        END IF;
END;
/

-- Mid-level tables
DELETE FROM notifications;
DELETE FROM money_requests;
DELETE FROM invoice;
DELETE FROM loans;
DELETE FROM transactions;
DELETE FROM cards;
DELETE FROM bank_accounts;
DELETE FROM wallet;

-- Root/parent tables last
DELETE FROM users;
DELETE FROM security_questions;

-- ============================================================
-- STEP 3: Re-enable all FK constraints
-- ============================================================
BEGIN
    FOR c IN (
        SELECT owner, constraint_name, table_name
        FROM   user_constraints
        WHERE  constraint_type = 'R'
    ) LOOP
        EXECUTE IMMEDIATE
            'ALTER TABLE ' || c.table_name ||
            ' ENABLE CONSTRAINT ' || c.constraint_name;
    END LOOP;
END;
/

-- ============================================================
-- STEP 4: Reset sequences to 1
--         (sequences are not reset by DELETE/TRUNCATE)
-- ============================================================
DECLARE
    PROCEDURE reset_seq(p_name IN VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || p_name;
        EXECUTE IMMEDIATE 'CREATE SEQUENCE ' || p_name || ' START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -2289 THEN 
                -- Sequence didn't exist to drop, just create it
                EXECUTE IMMEDIATE 'CREATE SEQUENCE ' || p_name || ' START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE';
            ELSE NULL; 
            END IF;
    END reset_seq;
BEGIN
    -- Core sequences (revpay_oracle_setup.sql names)
    reset_seq('USER_SEQ');
    reset_seq('WALLET_SEQ');
    reset_seq('CARD_SEQ');
    reset_seq('TRANSACTION_SEQ');
    reset_seq('REQUEST_SEQ');
    reset_seq('LOAN_SEQ');
    reset_seq('NOTIFICATION_SEQ');
    reset_seq('INVOICE_SEQ');
    reset_seq('INVOICE_ITEM_SEQ');
    reset_seq('SECURITY_QUESTION_SEQ');
    reset_seq('USER_SEC_ANSWERS_SEQ');
    reset_seq('BANK_ACCOUNT_SEQ');

    -- Alternate names (schema_mod*.sql names — safe to try both)
    reset_seq('USERS_SEQ');
    reset_seq('ROLES_SEQ');
    reset_seq('SEC_QUESTIONS_SEQ');
    reset_seq('TRANSACTIONS_SEQ');
    reset_seq('MONEY_REQUESTS_SEQ');
    reset_seq('NOTIFICATIONS_SEQ');
    reset_seq('NOTIF_PREF_SEQ');
    reset_seq('BUSINESS_PROF_SEQ');
    reset_seq('INVOICE_ITEMS_SEQ');
    reset_seq('LOANS_SEQ');
    reset_seq('REPAYMENTS_SEQ');
END;
/

-- ============================================================
-- STEP 5: Re-seed required lookup data
--         (Security questions must exist for registration)
-- ============================================================
INSERT INTO security_questions (question) VALUES ('What is your mother''s maiden name?');
INSERT INTO security_questions (question) VALUES ('What was the name of your first pet?');
INSERT INTO security_questions (question) VALUES ('What city were you born in?');
INSERT INTO security_questions (question) VALUES ('What was the name of your elementary school?');
INSERT INTO security_questions (question) VALUES ('What is your favourite childhood movie?');

COMMIT;

-- ============================================================
-- STEP 6: Verification — all tables should show 0 rows
-- ============================================================
SELECT 'users' AS tbl_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'wallet' AS tbl_name, COUNT(*) FROM wallet
UNION ALL
SELECT 'cards' AS tbl_name, COUNT(*) FROM cards
UNION ALL
SELECT 'bank_accounts' AS tbl_name, COUNT(*) FROM bank_accounts
UNION ALL
SELECT 'transactions' AS tbl_name, COUNT(*) FROM transactions
UNION ALL
SELECT 'money_requests' AS tbl_name, COUNT(*) FROM money_requests
UNION ALL
SELECT 'notifications' AS tbl_name, COUNT(*) FROM notifications
UNION ALL
SELECT 'invoice' AS tbl_name, COUNT(*) FROM invoice
UNION ALL
SELECT 'invoice_items' AS tbl_name, COUNT(*) FROM invoice_items
UNION ALL
SELECT 'loans' AS tbl_name, COUNT(*) FROM loans
UNION ALL
SELECT 'security_questions' AS tbl_name, COUNT(*) FROM security_questions
UNION ALL
SELECT 'user_security_answers' AS tbl_name, COUNT(*) FROM user_security_answers
ORDER BY 1;
-- Expected: all 0 except security_questions → 5 (re-seeded above)
