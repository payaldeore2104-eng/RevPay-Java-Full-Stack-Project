-- MODULE 2: Wallet & Payment Methods Schema & Procedures

CREATE SEQUENCE wallet_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE cards_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE wallet (
    id NUMBER PRIMARY KEY,
    user_id NUMBER UNIQUE NOT NULL,
    balance NUMBER(15,2) DEFAULT 0.0,
    currency   VARCHAR2(10) DEFAULT 'INR',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE cards (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL,
    card_number_encrypted VARCHAR2(255) NOT NULL,
    card_holder_name VARCHAR2(100) NOT NULL,
    expiry_date VARCHAR2(10) NOT NULL,
    cvv VARCHAR2(10) NOT NULL,
    billing_address VARCHAR2(255) NOT NULL,
    card_pin VARCHAR2(255),
    is_default NUMBER(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_card_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE OR REPLACE TRIGGER trg_wallet_id
BEFORE INSERT ON wallet
FOR EACH ROW
BEGIN
    SELECT wallet_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_cards_id
BEFORE INSERT ON cards
FOR EACH ROW
BEGIN
    SELECT cards_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

-- Requirement: Auto wallet creation after registration
CREATE OR REPLACE TRIGGER trg_auto_create_wallet
AFTER INSERT ON users
FOR EACH ROW
BEGIN
    INSERT INTO wallet (user_id, balance) VALUES (:NEW.id, 0.0);
END;
/

-- Stored Procedures
CREATE OR REPLACE PROCEDURE update_balance (
    p_user_id IN NUMBER,
    p_amount IN NUMBER, 
    p_out_new_balance OUT NUMBER
) AS
BEGIN
    UPDATE wallet 
    SET balance = balance + p_amount, updated_at = CURRENT_TIMESTAMP
    WHERE user_id = p_user_id
    RETURNING balance INTO p_out_new_balance;
    COMMIT;
END update_balance;
/

CREATE OR REPLACE PROCEDURE add_money (
    p_user_id IN NUMBER,
    p_amount IN NUMBER,
    p_out_new_balance OUT NUMBER
) AS
BEGIN
    IF p_amount <= 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Amount must be greater than zero');
    END IF;
    update_balance(p_user_id, p_amount, p_out_new_balance);
END add_money;
/

CREATE OR REPLACE PROCEDURE withdraw_money (
    p_user_id IN NUMBER,
    p_amount IN NUMBER,
    p_out_new_balance OUT NUMBER
) AS
    v_current_balance NUMBER;
BEGIN
    IF p_amount <= 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Amount must be greater than zero');
    END IF;

    SELECT balance INTO v_current_balance FROM wallet WHERE user_id = p_user_id FOR UPDATE;

    IF v_current_balance < p_amount THEN
        RAISE_APPLICATION_ERROR(-20002, 'Insufficient funds');
    ELSE
        update_balance(p_user_id, -p_amount, p_out_new_balance);
    END IF;
END withdraw_money;
/

-- Requirement: Low balance detection (Alert Trigger)
CREATE OR REPLACE TRIGGER trg_low_balance_alert
AFTER UPDATE OF balance ON wallet
FOR EACH ROW
WHEN (NEW.balance < 50.0 AND OLD.balance >= 50.0)
BEGIN
    -- Temporary logic, normally we insert into a NOTIFICATIONS table (Module 4)
    DBMS_OUTPUT.PUT_LINE('Low balance alert for user ' || :NEW.user_id || ': ' || :NEW.balance);
END;
/
