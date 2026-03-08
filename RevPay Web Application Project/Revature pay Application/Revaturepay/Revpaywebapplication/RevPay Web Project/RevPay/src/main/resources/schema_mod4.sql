-- MODULE 4: Notifications & Security Monitoring

CREATE SEQUENCE notifications_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE notif_pref_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE notification_preferences (
    id NUMBER PRIMARY KEY,
    user_id NUMBER UNIQUE NOT NULL,
    email_enabled NUMBER(1) DEFAULT 1,
    sms_enabled NUMBER(1) DEFAULT 1,
    in_app_enabled NUMBER(1) DEFAULT 1,
    CONSTRAINT fk_notif_pref_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE notifications (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL,
    title VARCHAR2(100) NOT NULL,
    message VARCHAR2(500) NOT NULL,
    type VARCHAR2(50) NOT NULL, -- TRANSACTION, ALERT, SECURITY
    is_read NUMBER(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE OR REPLACE TRIGGER trg_notifications_id
BEFORE INSERT ON notifications
FOR EACH ROW
BEGIN
    SELECT notifications_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_notif_pref_id
BEFORE INSERT ON notification_preferences
FOR EACH ROW
BEGIN
    SELECT notif_pref_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

-- Replace Module 3 & Module 2 dummy triggers with real ones:

CREATE OR REPLACE TRIGGER trg_tx_notification
AFTER INSERT ON transactions
FOR EACH ROW
BEGIN
    -- Notify Sender
    INSERT INTO notifications (user_id, title, message, type)
    VALUES (:NEW.sender_id, 'Money Sent', 'You sent ' || :NEW.amount || ' to user ID - ' || :NEW.receiver_id, 'TRANSACTION');

    -- Notify Receiver
    INSERT INTO notifications (user_id, title, message, type)
    VALUES (:NEW.receiver_id, 'Money Received', 'You received ' || :NEW.amount || ' from user ID - ' || :NEW.sender_id, 'TRANSACTION');
END;
/

CREATE OR REPLACE TRIGGER trg_req_notification
AFTER INSERT ON money_requests
FOR EACH ROW
BEGIN
    -- Notify Requestee
    INSERT INTO notifications (user_id, title, message, type)
    VALUES (:NEW.requestee_id, 'New Money Request', 'User ID ' || :NEW.requester_id || ' requested ' || :NEW.amount || ' from you.', 'TRANSACTION');
END;
/

CREATE OR REPLACE TRIGGER trg_low_balance_alert
AFTER UPDATE OF balance ON wallet
FOR EACH ROW
WHEN (NEW.balance < 50.0 AND OLD.balance >= 50.0)
BEGIN
    INSERT INTO notifications (user_id, title, message, type)
    VALUES (:NEW.user_id, 'Low Balance Alert', 'Your wallet balance has dropped below $50.00.', 'ALERT');
END;
/

CREATE OR REPLACE TRIGGER trg_failed_login_alert
AFTER UPDATE OF failed_attempts ON users
FOR EACH ROW
WHEN (NEW.failed_attempts > 0)
BEGIN
    IF :NEW.failed_attempts >= 5 THEN
        INSERT INTO notifications (user_id, title, message, type)
        VALUES (:NEW.id, 'Security Alert', 'Your account has been locked due to 5 failed login attempts.', 'SECURITY');
    ELSIF :NEW.failed_attempts = 1 THEN
        INSERT INTO notifications (user_id, title, message, type)
        VALUES (:NEW.id, 'Failed Login Attempt', 'A failed login attempt was detected on your account.', 'SECURITY');
    END IF;
END;
/
