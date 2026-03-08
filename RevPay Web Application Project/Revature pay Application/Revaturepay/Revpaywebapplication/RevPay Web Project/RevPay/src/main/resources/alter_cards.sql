-- Safe migration: add cvv and billing_address to cards table if they don't already exist

DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count
    FROM user_tab_columns
   WHERE table_name = 'CARDS' AND column_name = 'CVV';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE cards ADD cvv VARCHAR2(10)';
  END IF;
END;
/

DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_count
    FROM user_tab_columns
   WHERE table_name = 'CARDS' AND column_name = 'BILLING_ADDRESS';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE cards ADD billing_address VARCHAR2(255)';
  END IF;
END;
/
