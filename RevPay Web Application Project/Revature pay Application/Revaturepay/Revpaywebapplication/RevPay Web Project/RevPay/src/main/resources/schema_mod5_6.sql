-- MODULE 5: Business Operations Schema

CREATE SEQUENCE business_prof_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE invoice_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE invoice_items_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE business_profile (
    id NUMBER PRIMARY KEY,
    user_id NUMBER UNIQUE NOT NULL,
    verification_status VARCHAR2(50) DEFAULT 'PENDING',
    verification_document VARCHAR2(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bz_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE invoice (
    id NUMBER PRIMARY KEY,
    business_id NUMBER NOT NULL,
    customer_email VARCHAR2(100) NOT NULL,
    total_amount NUMBER(15,2) DEFAULT 0.0,
    status VARCHAR2(20) DEFAULT 'DRAFT', 
    due_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_bz FOREIGN KEY (business_id) REFERENCES users(id)
);

CREATE TABLE invoice_items (
    id NUMBER PRIMARY KEY,
    invoice_id NUMBER NOT NULL,
    description VARCHAR2(255) NOT NULL,
    quantity NUMBER NOT NULL,
    unit_price NUMBER(15,2) NOT NULL,
    tax_rate NUMBER(5,2) DEFAULT 0.0,
    line_total NUMBER(15,2) NOT NULL,
    CONSTRAINT fk_inv_item FOREIGN KEY (invoice_id) REFERENCES invoice(id)
);

CREATE OR REPLACE TRIGGER trg_bz_prof_id
BEFORE INSERT ON business_profile
FOR EACH ROW
BEGIN
    SELECT business_prof_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_invoice_id
BEFORE INSERT ON invoice
FOR EACH ROW
BEGIN
    SELECT invoice_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_invoice_items_id
BEFORE INSERT ON invoice_items
FOR EACH ROW
BEGIN
    SELECT invoice_items_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE PROCEDURE create_invoice (
    p_business_id IN NUMBER,
    p_customer_email IN VARCHAR2,
    p_due_date IN DATE,
    p_out_invoice_id OUT NUMBER
) AS
BEGIN
    INSERT INTO invoice (business_id, customer_email, due_date, status)
    VALUES (p_business_id, p_customer_email, p_due_date, 'DRAFT')
    RETURNING id INTO p_out_invoice_id;
    COMMIT;
END create_invoice;
/

CREATE OR REPLACE PROCEDURE update_invoice_status (
    p_invoice_id IN NUMBER,
    p_new_status IN VARCHAR2
) AS
BEGIN
    UPDATE invoice SET status = p_new_status, updated_at = CURRENT_TIMESTAMP WHERE id = p_invoice_id;
    COMMIT;
END update_invoice_status;
/

CREATE OR REPLACE PROCEDURE check_overdue_invoices AS
BEGIN
    UPDATE invoice 
    SET status = 'OVERDUE' 
    WHERE status IN ('DRAFT', 'SENT') 
    AND due_date < SYSDATE;
    COMMIT;
END check_overdue_invoices;
/

-- MODULE 6: Loans Schema

CREATE SEQUENCE loans_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE repayments_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE loans (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL,
    principal_amount NUMBER(15,2) NOT NULL,
    interest_rate NUMBER(5,2) NOT NULL,
    tenure_months NUMBER NOT NULL,
    emi_amount NUMBER(15,2),
    status VARCHAR2(20) DEFAULT 'PENDING', 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE repayments (
    id NUMBER PRIMARY KEY,
    loan_id NUMBER NOT NULL,
    amount_paid NUMBER(15,2) NOT NULL,
    payment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rep_loan FOREIGN KEY (loan_id) REFERENCES loans(id)
);

CREATE OR REPLACE TRIGGER trg_loans_id
BEFORE INSERT ON loans
FOR EACH ROW
BEGIN
    SELECT loans_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE TRIGGER trg_repayments_id
BEFORE INSERT ON repayments
FOR EACH ROW
BEGIN
    SELECT repayments_seq.NEXTVAL INTO :NEW.id FROM DUAL;
END;
/

CREATE OR REPLACE PROCEDURE calculate_emi (
    p_principal IN NUMBER,
    p_rate_annual IN NUMBER,
    p_months IN NUMBER,
    p_out_emi OUT NUMBER
) AS
    v_r NUMBER;
    v_math NUMBER;
BEGIN
    IF p_months = 0 THEN
        p_out_emi := 0;
        RETURN;
    END IF;
    v_r := p_rate_annual / 1200;
    IF v_r = 0 THEN
        p_out_emi := p_principal / p_months;
    ELSE
        v_math := POWER(1 + v_r, p_months);
        p_out_emi := p_principal * v_r * v_math / (v_math - 1);
    END IF;
END calculate_emi;
/

CREATE OR REPLACE PROCEDURE approve_loan (
    p_loan_id IN NUMBER,
    p_out_status OUT VARCHAR2
) AS
    v_user NUMBER;
    v_principal NUMBER;
    v_dummy NUMBER;
BEGIN
    SELECT user_id, principal_amount INTO v_user, v_principal FROM loans WHERE id = p_loan_id;
    add_money(v_user, v_principal, v_dummy);
    UPDATE loans SET status = 'APPROVED' WHERE id = p_loan_id;
    p_out_status := 'APPROVED';
    COMMIT;
END approve_loan;
/
