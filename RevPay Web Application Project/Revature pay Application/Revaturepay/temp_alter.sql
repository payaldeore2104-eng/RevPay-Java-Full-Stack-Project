ALTER TABLE cards ADD cvv VARCHAR2(10);
ALTER TABLE cards ADD billing_address VARCHAR2(255);
COMMIT;
exit;
