
SET DEFINE OFF;

-- ============================================================
-- SECTION 1: DROP EXISTING OBJECTS (safe re-run)
-- ============================================================
BEGIN
    FOR t IN (SELECT table_name FROM user_tables WHERE table_name IN (
        'INVOICE_ITEMS','INVOICE','LOANS','NOTIFICATIONS',
        'MONEY_REQUESTS','TRANSACTIONS','CARDS','WALLET',
        'USER_SECURITY_ANSWERS','SECURITY_QUESTIONS','USERS')) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS';
    END LOOP;
END;
/

BEGIN
    FOR s IN (SELECT sequence_name FROM user_sequences WHERE sequence_name IN (
        'USER_SEQ','WALLET_SEQ','CARD_SEQ','TRANSACTION_SEQ',
        'REQUEST_SEQ','LOAN_SEQ','NOTIFICATION_SEQ','INVOICE_SEQ',
        'INVOICE_ITEM_SEQ','SECURITY_QUESTION_SEQ','USER_SEC_ANSWERS_SEQ',
        'BANK_ACCOUNT_SEQ')) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/

-- ============================================================
-- SECTION 2: SEQUENCES
-- ============================================================
CREATE SEQUENCE user_seq          START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE wallet_seq        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE card_seq          START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE transaction_seq   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE request_seq       START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE loan_seq          START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE notification_seq  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE invoice_seq       START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE invoice_item_seq  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE security_question_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE user_sec_answers_seq  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE bank_account_seq      START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ============================================================
-- SECTION 3: TABLES
-- ============================================================

-- USERS
CREATE TABLE users (
    id            NUMBER PRIMARY KEY,
    full_name     VARCHAR2(100) NOT NULL,
    email         VARCHAR2(150) UNIQUE,
    phone         VARCHAR2(20)  UNIQUE,
    password_hash VARCHAR2(255) NOT NULL,
    transaction_pin VARCHAR2(255),
    business_name VARCHAR2(100),
    business_type VARCHAR2(50),
    tax_id        VARCHAR2(50),
    address       VARCHAR2(255),
    is_verified   NUMBER(1)     DEFAULT 0,
    role          VARCHAR2(20)  DEFAULT 'ROLE_PERSONAL' NOT NULL,
    is_active     NUMBER(1)     DEFAULT 1,
    login_attempts NUMBER(2)    DEFAULT 0,
    pref_notif_transactions NUMBER(1) DEFAULT 1,
    pref_notif_requests     NUMBER(1) DEFAULT 1,
    pref_notif_alerts       NUMBER(1) DEFAULT 1,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP
);

-- Wallet uses unique index created automatically by PRIMARY KEY/UNIQUE constraints

-- WALLET
CREATE TABLE wallet (
    id         NUMBER PRIMARY KEY,
    user_id    NUMBER NOT NULL REFERENCES users(id),
    balance    NUMBER(15,2) DEFAULT 0.00,
    currency   VARCHAR2(5)  DEFAULT 'INR',
    updated_at TIMESTAMP    DEFAULT SYSTIMESTAMP,
    CONSTRAINT wallet_user_unique UNIQUE(user_id)
);

-- CARDS
CREATE TABLE cards (
    id                   NUMBER PRIMARY KEY,
    user_id              NUMBER       NOT NULL REFERENCES users(id),
    card_holder_name     VARCHAR2(100) NOT NULL,
    card_number_encrypted VARCHAR2(500) NOT NULL,
    expiry_date          VARCHAR2(10),
    cvv                  VARCHAR2(10),
    billing_address      VARCHAR2(255),
    card_pin             VARCHAR2(255),
    is_default           NUMBER(1)    DEFAULT 0,
    created_at           TIMESTAMP    DEFAULT SYSTIMESTAMP
);

CREATE INDEX idx_cards_user_id ON cards(user_id);

-- BANK ACCOUNTS (Business Only)
CREATE TABLE bank_accounts (
    id                       NUMBER PRIMARY KEY,
    user_id                  NUMBER       NOT NULL REFERENCES users(id),
    bank_name                VARCHAR2(100) NOT NULL,
    account_number_encrypted VARCHAR2(500) NOT NULL,
    routing_number           VARCHAR2(50)  NOT NULL,
    created_at               TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE INDEX idx_bank_accts_user ON bank_accounts(user_id);

-- TRANSACTIONS
CREATE TABLE transactions (
    id               NUMBER PRIMARY KEY,
    sender_id        NUMBER        REFERENCES users(id),
    receiver_id      NUMBER        REFERENCES users(id),
    amount           NUMBER(15,2)  NOT NULL,
    transaction_type VARCHAR2(20)  DEFAULT 'TRANSFER',
    description      VARCHAR2(255),
    status           VARCHAR2(20)  DEFAULT 'COMPLETED',
    created_at       TIMESTAMP     DEFAULT SYSTIMESTAMP
);

CREATE INDEX idx_tx_sender   ON transactions(sender_id);
CREATE INDEX idx_tx_receiver ON transactions(receiver_id);

-- MONEY REQUESTS
CREATE TABLE money_requests (
    id            NUMBER PRIMARY KEY,
    requester_id  NUMBER       NOT NULL REFERENCES users(id),
    requestee_id  NUMBER       NOT NULL REFERENCES users(id),
    amount        NUMBER(15,2) NOT NULL,
    description   VARCHAR2(255),
    status        VARCHAR2(20) DEFAULT 'PENDING',
    created_at    TIMESTAMP    DEFAULT SYSTIMESTAMP,
    updated_at    TIMESTAMP
);

-- LOANS
CREATE TABLE loans (
    id                NUMBER PRIMARY KEY,
    user_id           NUMBER       NOT NULL REFERENCES users(id),
    principal_amount  NUMBER(15,2) NOT NULL,
    interest_rate     NUMBER(5,2)  DEFAULT 12.0,
    tenure_months     NUMBER(3)    NOT NULL,
    emi_amount        NUMBER(15,2),
    purpose           VARCHAR2(255),
    financial_details VARCHAR2(500),
    document_path     VARCHAR2(255),
    repaid_amount     NUMBER(15,2) DEFAULT 0,
    status            VARCHAR2(20) DEFAULT 'PENDING',
    created_at        TIMESTAMP    DEFAULT SYSTIMESTAMP
);

-- NOTIFICATIONS
CREATE TABLE notifications (
    id         NUMBER PRIMARY KEY,
    user_id    NUMBER        NOT NULL REFERENCES users(id),
    title      VARCHAR2(100),
    message    VARCHAR2(500) NOT NULL,
    type       VARCHAR2(50)  DEFAULT 'ALERT',
    is_read    NUMBER(1)     DEFAULT 0,
    created_at TIMESTAMP     DEFAULT SYSTIMESTAMP
);

-- INVOICE
CREATE TABLE invoice (
    id               NUMBER PRIMARY KEY,
    business_id      NUMBER        NOT NULL REFERENCES users(id),
    customer_email   VARCHAR2(150),
    customer_name    VARCHAR2(150),
    customer_address VARCHAR2(255),
    payment_terms    VARCHAR2(255),
    total_amount     NUMBER(15,2)  DEFAULT 0,
    due_date         DATE,
    status           VARCHAR2(20)  DEFAULT 'DRAFT',
    created_at       TIMESTAMP     DEFAULT SYSTIMESTAMP
);

-- INVOICE ITEMS
CREATE TABLE invoice_items (
    id           NUMBER PRIMARY KEY,
    invoice_id   NUMBER        NOT NULL REFERENCES invoice(id),
    description  VARCHAR2(255),
    quantity     NUMBER(6),
    unit_price   NUMBER(15,2),
    tax          NUMBER(15,2)  DEFAULT 0,
    line_total   NUMBER(15,2)
);

-- SECURITY QUESTIONS
CREATE TABLE security_questions (
    id       NUMBER PRIMARY KEY,
    question VARCHAR2(255) NOT NULL
);

-- USER SECURITY ANSWERS
CREATE TABLE user_security_answers (
    id          NUMBER PRIMARY KEY,
    user_id     NUMBER NOT NULL REFERENCES users(id),
    question_id NUMBER NOT NULL REFERENCES security_questions(id),
    answer_hash VARCHAR2(255) NOT NULL,
    CONSTRAINT user_question_unique UNIQUE(user_id, question_id)
);

-- ============================================================
-- SECTION 4: TRIGGERS (auto-increment via sequences)
-- ============================================================
CREATE OR REPLACE TRIGGER trg_users_id
    BEFORE INSERT ON users FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT user_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_wallet_id
    BEFORE INSERT ON wallet FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT wallet_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_card_id
    BEFORE INSERT ON cards FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT card_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_bank_acct_id
    BEFORE INSERT ON bank_accounts FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT bank_account_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_transaction_id
    BEFORE INSERT ON transactions FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT transaction_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_request_id
    BEFORE INSERT ON money_requests FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT request_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_loan_id
    BEFORE INSERT ON loans FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT loan_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_notification_id
    BEFORE INSERT ON notifications FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT notification_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_invoice_id
    BEFORE INSERT ON invoice FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT invoice_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_invoice_item_id
    BEFORE INSERT ON invoice_items FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT invoice_item_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_sec_question_id
    BEFORE INSERT ON security_questions FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT security_question_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

CREATE OR REPLACE TRIGGER trg_user_sec_answers_id
    BEFORE INSERT ON user_security_answers FOR EACH ROW
BEGIN IF :NEW.id IS NULL THEN SELECT user_sec_answers_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;
/

-- ============================================================
-- SECTION 5: STORED PROCEDURES
-- ============================================================

-- 5.1 ADD_MONEY  (called by wallet "Add Money" button)
CREATE OR REPLACE PROCEDURE add_money(
    p_user_id        IN  NUMBER,
    p_amount         IN  NUMBER,
    p_out_new_balance OUT NUMBER
) AS
BEGIN
    UPDATE wallet
       SET balance    = balance + p_amount,
           updated_at = SYSTIMESTAMP
     WHERE user_id = p_user_id;

    IF SQL%ROWCOUNT = 0 THEN
        INSERT INTO wallet(user_id, balance, currency, updated_at)
        VALUES (p_user_id, p_amount, 'INR', SYSTIMESTAMP);
    END IF;

    INSERT INTO transactions(sender_id, receiver_id, amount, transaction_type, description, status, created_at)
    VALUES(NULL, p_user_id, p_amount, 'DEPOSIT', 'Added funds to wallet', 'COMPLETED', SYSTIMESTAMP);

    SELECT balance INTO p_out_new_balance
      FROM wallet WHERE user_id = p_user_id;

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END add_money;
/

-- 5.2 WITHDRAW_MONEY
CREATE OR REPLACE PROCEDURE withdraw_money(
    p_user_id        IN  NUMBER,
    p_amount         IN  NUMBER,
    p_out_new_balance OUT NUMBER
) AS
    v_balance NUMBER;
BEGIN
    SELECT balance INTO v_balance FROM wallet WHERE user_id = p_user_id FOR UPDATE;

    IF v_balance < p_amount THEN
        RAISE_APPLICATION_ERROR(-20001, 'Insufficient balance');
    END IF;

    UPDATE wallet
       SET balance    = balance - p_amount,
           updated_at = SYSTIMESTAMP
     WHERE user_id = p_user_id;

    INSERT INTO transactions(sender_id, receiver_id, amount, transaction_type, description, status, created_at)
    VALUES(p_user_id, NULL, p_amount, 'WITHDRAWAL', 'Withdrew funds from wallet', 'COMPLETED', SYSTIMESTAMP);

    p_out_new_balance := v_balance - p_amount;
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END withdraw_money;
/

-- 5.3 TRANSFER_MONEY
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

    -- Debit sender
    UPDATE wallet SET balance = balance - p_amount, updated_at = SYSTIMESTAMP
     WHERE user_id = p_sender_id;

    -- Credit receiver (auto-create if missing)
    UPDATE wallet SET balance = balance + p_amount, updated_at = SYSTIMESTAMP
     WHERE user_id = p_receiver_id;

    IF SQL%ROWCOUNT = 0 THEN
        INSERT INTO wallet(user_id, balance, currency, updated_at)
        VALUES(p_receiver_id, p_amount, 'INR', SYSTIMESTAMP);
    END IF;

    -- Record transaction
    INSERT INTO transactions(sender_id, receiver_id, amount, transaction_type, description, status, created_at)
    VALUES(p_sender_id, p_receiver_id, p_amount, 'TRANSFER', p_description, 'COMPLETED', SYSTIMESTAMP)
    RETURNING id INTO p_out_tx_id;

    -- Notify sender & receiver
    INSERT INTO notifications(user_id, message) VALUES(p_sender_id,
        'You sent ₹' || p_amount || ' - ' || NVL(p_description, 'Transfer'));
    INSERT INTO notifications(user_id, message) VALUES(p_receiver_id,
        'You received ₹' || p_amount || ' - ' || NVL(p_description, 'Transfer'));

    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END transfer_money;
/

-- 5.4 CREATE_REQUEST
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

-- 5.5 UPDATE_REQUEST_STATUS
CREATE OR REPLACE PROCEDURE update_request_status(
    p_request_id  IN  NUMBER,
    p_user_id     IN  NUMBER,
    p_new_status  IN  VARCHAR2,
    p_out_status  OUT VARCHAR2
) AS
    v_requester_id  NUMBER;
    v_requestee_id  NUMBER;
    v_amount        NUMBER;
BEGIN
    SELECT requester_id, requestee_id, amount
      INTO v_requester_id, v_requestee_id, v_amount
      FROM money_requests WHERE id = p_request_id;

    UPDATE money_requests SET status = p_new_status WHERE id = p_request_id;

    IF p_new_status = 'ACCEPTED' THEN
        -- Auto transfer the money
        DECLARE v_tx_id NUMBER;
        BEGIN
            transfer_money(v_requestee_id, v_requester_id, v_amount, 'Money Request Payment', v_tx_id);
        END;
    END IF;

    p_out_status := p_new_status;
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END update_request_status;
/

-- 5.6 CALCULATE_EMI
CREATE OR REPLACE PROCEDURE calculate_emi(
    p_principal    IN  NUMBER,
    p_rate_annual  IN  NUMBER,
    p_months       IN  NUMBER,
    p_out_emi      OUT NUMBER
) AS
    v_monthly_rate NUMBER;
BEGIN
    v_monthly_rate := p_rate_annual / (12 * 100);
    IF v_monthly_rate = 0 THEN
        p_out_emi := p_principal / p_months;
    ELSE
        p_out_emi := ROUND(p_principal * v_monthly_rate *
                     POWER(1 + v_monthly_rate, p_months) /
                     (POWER(1 + v_monthly_rate, p_months) - 1), 2);
    END IF;
END calculate_emi;
/

-- 5.7 CREATE_INVOICE
CREATE OR REPLACE PROCEDURE create_invoice(
    p_business_id      IN  NUMBER,
    p_customer_email   IN  VARCHAR2,
    p_customer_name    IN  VARCHAR2,
    p_customer_address IN  VARCHAR2,
    p_payment_terms    IN  VARCHAR2,
    p_due_date         IN  DATE,
    p_out_invoice_id   OUT NUMBER
) AS
BEGIN
    INSERT INTO invoice(business_id, customer_email, customer_name, customer_address, payment_terms, due_date, status)
    VALUES(p_business_id, p_customer_email, p_customer_name, p_customer_address, p_payment_terms, p_due_date, 'DRAFT')
    RETURNING id INTO p_out_invoice_id;
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END create_invoice;
/

-- 5.8 UPDATE_INVOICE_STATUS
CREATE OR REPLACE PROCEDURE update_invoice_status(
    p_invoice_id  IN NUMBER,
    p_new_status  IN VARCHAR2
) AS
BEGIN
    UPDATE invoice SET status = p_new_status WHERE id = p_invoice_id;
    COMMIT;
EXCEPTION
    WHEN OTHERS THEN ROLLBACK; RAISE;
END update_invoice_status;
/

-- ============================================================
-- SECTION 6: SAMPLE TEST DATA (optional - safe to skip)
-- ============================================================
-- Test Admin User (password = 'admin123' bcrypt-hashed)
INSERT INTO users(full_name, email, phone, password_hash, role)
VALUES('Admin User', 'admin@revpay.com', '9000000000',
       '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ROLE_ADMIN');

-- Wallet for admin
INSERT INTO wallet(user_id, balance, currency) VALUES(user_seq.CURRVAL, 10000.00, 'INR');

-- Insert security questions
INSERT INTO security_questions (id, question) VALUES (security_question_seq.NEXTVAL, 'What is your mother''s maiden name?');
INSERT INTO security_questions (id, question) VALUES (security_question_seq.NEXTVAL, 'What was the name of your first pet?');
INSERT INTO security_questions (id, question) VALUES (security_question_seq.NEXTVAL, 'What city were you born in?');

COMMIT;

-- ============================================================
-- SECTION 7: VERIFY SETUP
-- ============================================================
SELECT 'users'          AS tbl, COUNT(*) AS row_count FROM users          UNION ALL
SELECT 'wallet'         AS tbl, COUNT(*) AS row_count FROM wallet         UNION ALL
SELECT 'cards'          AS tbl, COUNT(*) AS row_count FROM cards          UNION ALL
SELECT 'transactions'   AS tbl, COUNT(*) AS row_count FROM transactions   UNION ALL
SELECT 'money_requests' AS tbl, COUNT(*) AS row_count FROM money_requests UNION ALL
SELECT 'loans'          AS tbl, COUNT(*) AS row_count FROM loans          UNION ALL
SELECT 'notifications'  AS tbl, COUNT(*) AS row_count FROM notifications  UNION ALL
SELECT 'invoice'        AS tbl, COUNT(*) AS row_count FROM invoice;
