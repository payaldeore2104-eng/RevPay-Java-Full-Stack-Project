
-- 1. Add reference_url to notifications table
ALTER TABLE notifications ADD (reference_url VARCHAR2(500));

-- 2. Add transaction_ref_id to transactions table (human-readable: TXN-00001234)
ALTER TABLE transactions ADD (transaction_ref_id VARCHAR2(20));

-- 3. Back-fill existing transactions with reference IDs
UPDATE transactions SET transaction_ref_id = 'TXN-' || LPAD(id, 8, '0') WHERE transaction_ref_id IS NULL;

-- 4. Create a trigger so future inserts auto-assign the ref ID
CREATE OR REPLACE TRIGGER trg_transaction_ref_id
BEFORE INSERT ON transactions
FOR EACH ROW
BEGIN
    IF :NEW.transaction_ref_id IS NULL THEN
        SELECT 'TXN-' || LPAD(:NEW.id, 8, '0') INTO :NEW.transaction_ref_id FROM dual;
    END IF;
END;
/

COMMIT;
