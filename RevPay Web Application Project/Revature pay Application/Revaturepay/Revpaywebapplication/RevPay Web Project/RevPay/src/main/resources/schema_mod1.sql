-- MODULE 1: User Management Schema & Procedures

-- Sequences
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE roles_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE sec_questions_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE user_sec_answers_seq START WITH 1 INCREMENT BY 1;

-- Tables
CREATE TABLE roles (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(50) UNIQUE NOT NULL
);

CREATE TABLE security_questions (
    id NUMBER PRIMARY KEY,
    question VARCHAR2(255) NOT NULL
);

CREATE TABLE users (
    id NUMBER PRIMARY KEY,
    full_name VARCHAR2(100) NOT NULL,
    email VARCHAR2(100) UNIQUE NOT NULL,
    phone VARCHAR2(20) UNIQUE NOT NULL,
    password_hash VARCHAR2(255) NOT NULL,
    transaction_pin VARCHAR2(255),
    role_id NUMBER NOT NULL,
    business_name VARCHAR2(100),
    business_type VARCHAR2(100),
    tax_id VARCHAR2(50),
    address VARCHAR2(255),
    is_verified NUMBER(1) DEFAULT 0,
    failed_attempts NUMBER DEFAULT 0,
    is_locked NUMBER(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

CREATE TABLE user_security_answers (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL,
    question_id NUMBER NOT NULL,
    answer_hash VARCHAR2(255) NOT NULL,
    CONSTRAINT fk_answer_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_answer_question FOREIGN KEY (question_id) REFERENCES security_questions(id)
);

-- Triggers for Auto-Increment
CREATE OR REPLACE TRIGGER trg_users_id
BEFORE INSERT ON users
FOR EACH ROW
BEGIN
    SELECT users_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_roles_id
BEFORE INSERT ON roles
FOR EACH ROW
BEGIN
    SELECT roles_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_sec_questions_id
BEFORE INSERT ON security_questions
FOR EACH ROW
BEGIN
    SELECT sec_questions_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_user_sec_answers_id
BEFORE INSERT ON user_security_answers
FOR EACH ROW
BEGIN
    SELECT user_sec_answers_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

-- Initial Data
INSERT INTO roles (name) VALUES ('PERSONAL');
INSERT INTO roles (name) VALUES ('BUSINESS');

INSERT INTO security_questions (question) VALUES ('What was the name of your first pet?');
INSERT INTO security_questions (question) VALUES ('In what city were you born?');
INSERT INTO security_questions (question) VALUES ('What is your mother''s maiden name?');

-- PROCEDURES
CREATE OR REPLACE PROCEDURE register_user (
    p_full_name IN VARCHAR2,
    p_email IN VARCHAR2,
    p_phone IN VARCHAR2,
    p_password_hash IN VARCHAR2,
    p_role_name IN VARCHAR2,
    p_business_name IN VARCHAR2 DEFAULT NULL,
    p_business_type IN VARCHAR2 DEFAULT NULL,
    p_tax_id IN VARCHAR2 DEFAULT NULL,
    p_address IN VARCHAR2 DEFAULT NULL,
    p_out_user_id OUT NUMBER
) AS
    v_role_id NUMBER;
BEGIN
    SELECT id INTO v_role_id FROM roles WHERE name = p_role_name;

    INSERT INTO users (
        full_name, email, phone, password_hash, role_id, 
        business_name, business_type, tax_id, address
    ) VALUES (
        p_full_name, p_email, p_phone, p_password_hash, v_role_id,
        p_business_name, p_business_type, p_tax_id, p_address
    ) RETURNING id INTO p_out_user_id;
    
    COMMIT;
END register_user;
/

CREATE OR REPLACE PROCEDURE validate_login (
    p_login_id IN VARCHAR2, 
    p_out_status OUT VARCHAR2,
    p_out_hash OUT VARCHAR2,
    p_out_user_id OUT NUMBER
) AS
    v_user_rec users%ROWTYPE;
BEGIN
    SELECT * INTO v_user_rec 
    FROM users 
    WHERE email = p_login_id OR phone = p_login_id;
    
    IF v_user_rec.is_locked = 1 THEN
        p_out_status := 'LOCKED';
        p_out_hash := NULL;
        p_out_user_id := v_user_rec.id;
    ELSE
        p_out_status := 'OK';
        p_out_hash := v_user_rec.password_hash;
        p_out_user_id := v_user_rec.id;
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_out_status := 'NOT_FOUND';
        p_out_hash := NULL;
        p_out_user_id := NULL;
END validate_login;
/

CREATE OR REPLACE PROCEDURE update_failed_attempts (
    p_user_id IN NUMBER,
    p_out_locked OUT NUMBER
) AS
    v_attempts NUMBER;
BEGIN
    UPDATE users SET failed_attempts = failed_attempts + 1
    WHERE id = p_user_id
    RETURNING failed_attempts INTO v_attempts;
    
    IF v_attempts >= 5 THEN
        UPDATE users SET is_locked = 1 WHERE id = p_user_id;
        p_out_locked := 1;
    ELSE
        p_out_locked := 0;
    END IF;
    COMMIT;
END update_failed_attempts;
/

CREATE OR REPLACE PROCEDURE reset_failed_attempts (
    p_user_id IN NUMBER
) AS
BEGIN
    UPDATE users SET failed_attempts = 0, is_locked = 0 WHERE id = p_user_id;
    COMMIT;
END reset_failed_attempts;
/

CREATE OR REPLACE PROCEDURE reset_password (
    p_user_id IN NUMBER,
    p_new_password_hash IN VARCHAR2
) AS
BEGIN
    UPDATE users SET password_hash = p_new_password_hash, failed_attempts = 0, is_locked = 0 WHERE id = p_user_id;
    COMMIT;
END reset_password;
/

CREATE OR REPLACE PROCEDURE validate_pin (
    p_user_id IN NUMBER,
    p_out_pin_hash OUT VARCHAR2
) AS
BEGIN
    SELECT transaction_pin INTO p_out_pin_hash FROM users WHERE id = p_user_id;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_out_pin_hash := NULL;
END validate_pin;
/
