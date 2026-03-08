-- MODULE 3: Transactions & Money Requests Schema & Procedures

CREATE SEQUENCE transactions_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE money_requests_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE transactions (
    id NUMBER PRIMARY KEY,
    sender_id NUMBER NOT NULL,
    receiver_id NUMBER NOT NULL,
    amount NUMBER(15,2) NOT NULL,
    status VARCHAR2(20) DEFAULT 'COMPLETED', -- PENDING, COMPLETED, FAILED
    transaction_type VARCHAR2(20) DEFAULT 'TRANSFER', -- TRANSFER, REQUEST_PAYMENT
    description VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_tx_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
);

CREATE TABLE money_requests (
    id NUMBER PRIMARY KEY,
    requester_id NUMBER NOT NULL,
    requestee_id NUMBER NOT NULL,
    amount NUMBER(15,2) NOT NULL,
    status VARCHAR2(20) DEFAULT 'PENDING', -- PENDING, ACCEPTED, DECLINED, CANCELLED
    description VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_req_requester FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_req_requestee FOREIGN KEY (requestee_id) REFERENCES users(id)
);

CREATE OR REPLACE TRIGGER trg_transactions_id
BEFORE INSERT ON transactions
FOR EACH ROW
BEGIN
    SELECT transactions_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_money_requests_id
BEFORE INSERT ON money_requests
FOR EACH ROW
BEGIN
    SELECT money_requests_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

-- Stored Procedures

CREATE OR REPLACE PROCEDURE transfer_money (
    p_sender_id IN NUMBER,
    p_receiver_id IN NUMBER,
    p_amount IN NUMBER,
    p_description IN VARCHAR2,
    p_out_tx_id OUT NUMBER
) AS
    v_sender_bal NUMBER;
BEGIN
    IF p_amount <= 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Amount must be greater than zero');
    END IF;

    SELECT balance INTO v_sender_bal FROM wallet WHERE user_id = p_sender_id FOR UPDATE;

    IF v_sender_bal < p_amount THEN
        RAISE_APPLICATION_ERROR(-20002, 'Insufficient funds for transfer');
    END IF;

    -- Deduct
    UPDATE wallet SET balance = balance - p_amount, updated_at = CURRENT_TIMESTAMP WHERE user_id = p_sender_id;
    -- Add
    UPDATE wallet SET balance = balance + p_amount, updated_at = CURRENT_TIMESTAMP WHERE user_id = p_receiver_id;

    -- Log transaction
    INSERT INTO transactions (sender_id, receiver_id, amount, status, transaction_type, description)
    VALUES (p_sender_id, p_receiver_id, p_amount, 'COMPLETED', 'TRANSFER', p_description)
    RETURNING id INTO p_out_tx_id;
    
    COMMIT;
END transfer_money;
/

CREATE OR REPLACE PROCEDURE create_request (
    p_requester_id IN NUMBER,
    p_requestee_id IN NUMBER,
    p_amount IN NUMBER,
    p_description IN VARCHAR2,
    p_out_req_id OUT NUMBER
) AS
BEGIN
    IF p_amount <= 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Amount must be greater than zero');
    END IF;

    INSERT INTO money_requests (requester_id, requestee_id, amount, status, description)
    VALUES (p_requester_id, p_requestee_id, p_amount, 'PENDING', p_description)
    RETURNING id INTO p_out_req_id;
    
    COMMIT;
END create_request;
/

CREATE OR REPLACE PROCEDURE update_request_status (
    p_request_id IN NUMBER,
    p_user_id IN NUMBER,
    p_new_status IN VARCHAR2,
    p_out_status OUT VARCHAR2
) AS
    v_req money_requests%ROWTYPE;
    v_dummy_tx NUMBER;
BEGIN
    SELECT * INTO v_req FROM money_requests WHERE id = p_request_id FOR UPDATE;

    IF v_req.status != 'PENDING' THEN
        RAISE_APPLICATION_ERROR(-20003, 'Request is not in PENDING state');
    END IF;

    IF p_new_status = 'ACCEPTED' THEN
        -- Transfer from requestee to requester
        transfer_money(v_req.requestee_id, v_req.requester_id, v_req.amount, 'Payment for Request ID ' || p_request_id, v_dummy_tx);
    END IF;

    UPDATE money_requests SET status = p_new_status, updated_at = CURRENT_TIMESTAMP WHERE id = p_request_id;
    p_out_status := p_new_status;
    
    COMMIT;
END update_request_status;
/

-- Requirement: Notification creation triggers
CREATE OR REPLACE TRIGGER trg_tx_notification
AFTER INSERT ON transactions
FOR EACH ROW
BEGIN
    DBMS_OUTPUT.PUT_LINE('Notify Sender: Sent ' || :NEW.amount);
    DBMS_OUTPUT.PUT_LINE('Notify Receiver: Received ' || :NEW.amount);
END;
/
