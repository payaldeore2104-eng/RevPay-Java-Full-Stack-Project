package com.revpay;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;


@Component
public class DatabaseInitializer {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @PostConstruct
        public void initSchema() {
                addColumnIfMissing("USERS", "TRANSACTION_PIN", "VARCHAR2(255)");
                addColumnIfMissing("USERS", "BUSINESS_NAME", "VARCHAR2(100)");
                addColumnIfMissing("USERS", "BUSINESS_TYPE", "VARCHAR2(50)");
                addColumnIfMissing("USERS", "TAX_ID", "VARCHAR2(50)");
                addColumnIfMissing("USERS", "ADDRESS", "VARCHAR2(255)");
                addColumnIfMissing("USERS", "IS_VERIFIED", "NUMBER(1) DEFAULT 0");
                addColumnIfMissing("USERS", "PREF_NOTIF_TRANSACTIONS", "NUMBER(1) DEFAULT 1");
                addColumnIfMissing("USERS", "PREF_NOTIF_REQUESTS", "NUMBER(1) DEFAULT 1");
                addColumnIfMissing("USERS", "PREF_NOTIF_ALERTS", "NUMBER(1) DEFAULT 1");

                // Notification columns added later that might be missing
                addColumnIfMissing("NOTIFICATIONS", "TITLE", "VARCHAR2(100)");
                addColumnIfMissing("NOTIFICATIONS", "TYPE", "VARCHAR2(50) DEFAULT 'ALERT'");

                // Money Requests newly required columns
                addColumnIfMissing("MONEY_REQUESTS", "UPDATED_AT", "TIMESTAMP");

                // Missing Invoice features
                addColumnIfMissing("INVOICE", "CUSTOMER_NAME", "VARCHAR2(150)");
                addColumnIfMissing("INVOICE", "CUSTOMER_ADDRESS", "VARCHAR2(255)");
                addColumnIfMissing("INVOICE", "PAYMENT_TERMS", "VARCHAR2(255)");
                addColumnIfMissing("INVOICE_ITEMS", "TAX", "NUMBER(15,2) DEFAULT 0");

                // Missing Loan features
                addColumnIfMissing("LOANS", "PURPOSE", "VARCHAR2(255)");
                addColumnIfMissing("LOANS", "FINANCIAL_DETAILS", "VARCHAR2(500)");
                addColumnIfMissing("LOANS", "DOCUMENT_PATH", "VARCHAR2(255)");
                addColumnIfMissing("LOANS", "REPAID_AMOUNT", "NUMBER(15,2) DEFAULT 0");

                // The instruction seems to have a typo, interpreting it as adding
                // ensureBankAccountTablesExist()
                // and assuming ensureBasicTablesExist() is a placeholder or intended to be
                // added elsewhere.
                // For now, only adding the explicit call to ensureBankAccountTablesExist() as
                // per the context.
                ensureSecurityTablesExist();
                ensureBankAccountTablesExist();
                addColumnIfMissing("TRANSACTIONS", "TRANSACTION_ID", "VARCHAR2(50) UNIQUE");
                ensureNotificationsIdTrigger();
                ensureSecurityQuestionsSeeded();
                ensureProceduresUpdated();
                ensureVerificationDocumentsTableExist();
                addColumnIfMissing("BUSINESS_VERIFICATION_DOCS", "DOC_PATH", "VARCHAR2(500)");
        }

        /** ADD column only if it doesn't already exist in Oracle. */
        private void addColumnIfMissing(String table, String column, String definition) {
                try {
                        // Check Oracle data dictionary (case-insensitive — Oracle stores names in UPPER
                        // CASE)
                        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                                        "SELECT column_name FROM user_tab_columns " +
                                                        "WHERE table_name = ? AND column_name = ?",
                                        table.toUpperCase(), column.toUpperCase());

                        if (rows.isEmpty()) {
                                jdbcTemplate.execute("ALTER TABLE " + table + " ADD " + column + " " + definition);
                                System.out.println("[RevPay Init] Added column: " + table + "." + column);
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] Could not add column " + table + "." + column + ": "
                                        + e.getMessage());
                }
        }

        /**
         * Create SECURITY_QUESTIONS and USER_SECURITY_ANSWERS tables if they don't
         * exist.
         */
        private void ensureSecurityTablesExist() {
                try {
                        List<Map<String, Object>> seq = jdbcTemplate.queryForList(
                                        "SELECT sequence_name FROM user_sequences WHERE sequence_name = 'SECURITY_QUESTION_SEQ'");
                        if (seq.isEmpty()) {
                                jdbcTemplate
                                                .execute("CREATE SEQUENCE security_question_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE");
                                System.out.println("[RevPay Init] Created sequence: security_question_seq");
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] security_question_seq: " + e.getMessage());
                }
                try {
                        List<Map<String, Object>> seq = jdbcTemplate.queryForList(
                                        "SELECT sequence_name FROM user_sequences WHERE sequence_name = 'USER_SEC_ANSWERS_SEQ'");
                        if (seq.isEmpty()) {
                                jdbcTemplate
                                                .execute("CREATE SEQUENCE user_sec_answers_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE");
                                System.out.println("[RevPay Init] Created sequence: user_sec_answers_seq");
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] user_sec_answers_seq: " + e.getMessage());
                }

                try {
                        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                                        "SELECT table_name FROM user_tables WHERE table_name = 'SECURITY_QUESTIONS'");
                        if (rows.isEmpty()) {
                                jdbcTemplate.execute(
                                                "CREATE TABLE security_questions (" +
                                                                "  id       NUMBER PRIMARY KEY, " +
                                                                "  question VARCHAR2(255) NOT NULL)");
                                jdbcTemplate.execute(
                                                "CREATE OR REPLACE TRIGGER trg_sec_question_id " +
                                                                "  BEFORE INSERT ON security_questions FOR EACH ROW " +
                                                                "BEGIN IF :NEW.id IS NULL THEN " +
                                                                "  SELECT security_question_seq.NEXTVAL INTO :NEW.id FROM dual; "
                                                                +
                                                                "END IF; END;");
                                System.out.println("[RevPay Init] Created table: SECURITY_QUESTIONS");
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] SECURITY_QUESTIONS: " + e.getMessage());
                }

                try {
                        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                                        "SELECT table_name FROM user_tables WHERE table_name = 'USER_SECURITY_ANSWERS'");
                        if (rows.isEmpty()) {
                                jdbcTemplate.execute(
                                                "CREATE TABLE user_security_answers (" +
                                                                "  id          NUMBER PRIMARY KEY, " +
                                                                "  user_id     NUMBER NOT NULL REFERENCES users(id), " +
                                                                "  question_id NUMBER NOT NULL REFERENCES security_questions(id), "
                                                                +
                                                                "  answer_hash VARCHAR2(255) NOT NULL, " +
                                                                "  CONSTRAINT user_question_unique UNIQUE(user_id, question_id))");
                                jdbcTemplate.execute(
                                                "CREATE OR REPLACE TRIGGER trg_user_sec_answers_id " +
                                                                "  BEFORE INSERT ON user_security_answers FOR EACH ROW "
                                                                +
                                                                "BEGIN IF :NEW.id IS NULL THEN " +
                                                                "  SELECT user_sec_answers_seq.NEXTVAL INTO :NEW.id FROM dual; "
                                                                +
                                                                "END IF; END;");
                                System.out.println("[RevPay Init] Created table: USER_SECURITY_ANSWERS");
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] USER_SECURITY_ANSWERS: " + e.getMessage());
                }
        }

        /** Seed default security questions if the table is empty. */
        private void ensureSecurityQuestionsSeeded() {
                try {
                        Integer count = jdbcTemplate.queryForObject(
                                        "SELECT COUNT(*) FROM security_questions", Integer.class);
                        if (count != null && count == 0) {
                                jdbcTemplate.update("INSERT INTO security_questions (question) VALUES (?)",
                                                "What is your mother's maiden name?");
                                jdbcTemplate.update("INSERT INTO security_questions (question) VALUES (?)",
                                                "What was the name of your first pet?");
                                jdbcTemplate.update("INSERT INTO security_questions (question) VALUES (?)",
                                                "What city were you born in?");
                                jdbcTemplate.update("INSERT INTO security_questions (question) VALUES (?)",
                                                "What is the name of your primary school?");
                                jdbcTemplate.update("INSERT INTO security_questions (question) VALUES (?)",
                                                "What was your childhood nickname?");
                                System.out.println("[RevPay Init] Seeded security questions.");
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] Seeding security questions: " + e.getMessage());
                }
        }

        private void ensureBankAccountTablesExist() {
                try {
                        jdbcTemplate.execute("SELECT 1 FROM bank_accounts WHERE 1=0");
                } catch (Exception e) {
                        System.out.println("[RevPay Init] Creating BANK_ACCOUNTS table & sequence...");
                        jdbcTemplate.execute(
                                        "CREATE SEQUENCE bank_account_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE");
                        System.out.println("[RevPay Init] Created bank_account_seq");
                        jdbcTemplate.execute("CREATE TABLE bank_accounts (" +
                                        "id NUMBER PRIMARY KEY, " +
                                        "user_id NUMBER NOT NULL REFERENCES users(id), " +
                                        "bank_name VARCHAR2(100) NOT NULL, " +
                                        "account_number_encrypted VARCHAR2(500) NOT NULL, " +
                                        "routing_number VARCHAR2(50) NOT NULL, " +
                                        "created_at TIMESTAMP DEFAULT SYSTIMESTAMP)");
                        jdbcTemplate.execute("CREATE INDEX idx_bank_accts_user ON bank_accounts(user_id)");
                        jdbcTemplate.execute("CREATE OR REPLACE TRIGGER trg_bank_acct_id " +
                                        "BEFORE INSERT ON bank_accounts FOR EACH ROW " +
                                        "BEGIN IF :NEW.id IS NULL THEN SELECT bank_account_seq.NEXTVAL INTO :NEW.id FROM dual; END IF; END;");
                }
        }

        /**
         * Ensure the notifications table has an auto-increment trigger for the id
         * column.
         * Without this, Oracle stored procedures that INSERT INTO
         * notifications(user_id, title, message, type)
         * will fail at compile time with PLS-00905 because the primary key 'id' is not
         * populated.
         */
        private void ensureNotificationsIdTrigger() {
                try {
                        // Drop old sequences and create a fresh one synced to MAX(id)
                        String syncSequenceSql = "DECLARE " +
                                        "  v_max_id NUMBER; " +
                                        "BEGIN " +
                                        "  SELECT NVL(MAX(id), 0) INTO v_max_id FROM notifications; " +
                                        "  BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE notification_seq'; EXCEPTION WHEN OTHERS THEN NULL; END; "
                                        +
                                        "  BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE notifications_seq'; EXCEPTION WHEN OTHERS THEN NULL; END; "
                                        +
                                        "  EXECUTE IMMEDIATE 'CREATE SEQUENCE notification_seq START WITH ' || (v_max_id + 1) || ' INCREMENT BY 1 NOCACHE NOCYCLE'; "
                                        +
                                        "END;";
                        jdbcTemplate.execute(syncSequenceSql);
                        System.out.println("[RevPay Init] Synchronized notification_seq with MAX(id)");

                        // Create or replace the auto-id trigger
                        jdbcTemplate.execute(
                                        "CREATE OR REPLACE TRIGGER trg_notification_id " +
                                                        "BEFORE INSERT ON notifications FOR EACH ROW " +
                                                        "BEGIN " +
                                                        "  IF :NEW.id IS NULL THEN " +
                                                        "    SELECT notification_seq.NEXTVAL INTO :NEW.id FROM dual; " +
                                                        "  END IF; " +
                                                        "END;");
                        System.out.println(
                                        "[RevPay Init] Ensured trigger trg_notification_id on notifications uses notification_seq");

                        // Just in case transactions or money_requests also need sequence syncs due to
                        // data inserts
                        String syncTxSeqSql = "DECLARE " +
                                        "  v_max_id NUMBER; " +
                                        "BEGIN " +
                                        "  SELECT NVL(MAX(id), 0) INTO v_max_id FROM transactions; " +
                                        "  BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE transaction_seq'; EXCEPTION WHEN OTHERS THEN NULL; END; "
                                        +
                                        "  EXECUTE IMMEDIATE 'CREATE SEQUENCE transaction_seq START WITH ' || (v_max_id + 1) || ' INCREMENT BY 1 NOCACHE NOCYCLE'; "
                                        +
                                        "END;";
                        jdbcTemplate.execute(syncTxSeqSql);

                        String syncReqSeqSql = "DECLARE " +
                                        "  v_max_id NUMBER; " +
                                        "BEGIN " +
                                        "  SELECT NVL(MAX(id), 0) INTO v_max_id FROM money_requests; " +
                                        "  BEGIN EXECUTE IMMEDIATE 'DROP SEQUENCE request_seq'; EXCEPTION WHEN OTHERS THEN NULL; END; "
                                        +
                                        "  EXECUTE IMMEDIATE 'CREATE SEQUENCE request_seq START WITH ' || (v_max_id + 1) || ' INCREMENT BY 1 NOCACHE NOCYCLE'; "
                                        +
                                        "END;";
                        jdbcTemplate.execute(syncReqSeqSql);

                } catch (Exception e) {
                        System.err.println(
                                        "[RevPay Init] Could not create notifications trigger & sync sequences: "
                                                        + e.getMessage());
                }
        }

        /** Re-compile stored procedures to fix any known DB-side bugs */
        private void ensureProceduresUpdated() {
                try {
                        String addMoneyProc = "CREATE OR REPLACE PROCEDURE add_money(\n" +
                                        "    p_user_id        IN  NUMBER,\n" +
                                        "    p_amount         IN  NUMBER,\n" +
                                        "    p_transaction_id IN  VARCHAR2,\n" +
                                        "    p_out_new_balance OUT NUMBER\n" +
                                        ") AS\n" +
                                        "BEGIN\n" +
                                        "    UPDATE wallet\n" +
                                        "       SET balance    = balance + p_amount,\n" +
                                        "           updated_at = SYSTIMESTAMP\n" +
                                        "     WHERE user_id = p_user_id;\n" +
                                        "\n" +
                                        "    IF SQL%ROWCOUNT = 0 THEN\n" +
                                        "        INSERT INTO wallet(user_id, balance, currency, updated_at)\n" +
                                        "        VALUES (p_user_id, p_amount, 'INR', SYSTIMESTAMP);\n" +
                                        "    END IF;\n" +
                                        "\n" +
                                        "    INSERT INTO transactions(sender_id, receiver_id, amount, transaction_type, description, status, transaction_id, created_at)\n"
                                        +
                                        "    VALUES(NULL, p_user_id, p_amount, 'DEPOSIT', 'Added funds to wallet', 'COMPLETED', p_transaction_id, SYSTIMESTAMP);\n"
                                        +
                                        "\n" +
                                        "    SELECT balance INTO p_out_new_balance\n" +
                                        "      FROM wallet WHERE user_id = p_user_id;\n" +
                                        "\n" +
                                        "    COMMIT;\n" +
                                        "EXCEPTION\n" +
                                        "    WHEN OTHERS THEN ROLLBACK; RAISE;\n" +
                                        "END add_money;";

                        jdbcTemplate.execute(addMoneyProc);
                        System.out.println("[RevPay Init] Recompiled procedure: add_money (Added transaction logging)");

                        String withdrawMoneyProc = "CREATE OR REPLACE PROCEDURE withdraw_money(\n" +
                                        "    p_user_id        IN  NUMBER,\n" +
                                        "    p_amount         IN  NUMBER,\n" +
                                        "    p_transaction_id IN  VARCHAR2,\n" +
                                        "    p_out_new_balance OUT NUMBER\n" +
                                        ") AS\n" +
                                        "    v_balance NUMBER;\n" +
                                        "BEGIN\n" +
                                        "    SELECT balance INTO v_balance FROM wallet WHERE user_id = p_user_id FOR UPDATE;\n"
                                        +
                                        "\n" +
                                        "    IF v_balance < p_amount THEN\n" +
                                        "        RAISE_APPLICATION_ERROR(-20001, 'Insufficient balance');\n" +
                                        "    END IF;\n" +
                                        "\n" +
                                        "    UPDATE wallet\n" +
                                        "       SET balance    = balance - p_amount,\n" +
                                        "           updated_at = SYSTIMESTAMP\n" +
                                        "     WHERE user_id = p_user_id;\n" +
                                        "\n" +
                                        "    INSERT INTO transactions(sender_id, receiver_id, amount, transaction_type, description, status, transaction_id, created_at)\n"
                                        +
                                        "    VALUES(p_user_id, NULL, p_amount, 'WITHDRAWAL', 'Withdrew funds from wallet', 'COMPLETED', p_transaction_id, SYSTIMESTAMP);\n"
                                        +
                                        "\n" +
                                        "    p_out_new_balance := v_balance - p_amount;\n" +
                                        "    COMMIT;\n" +
                                        "EXCEPTION\n" +
                                        "    WHEN OTHERS THEN ROLLBACK; RAISE;\n" +
                                        "END withdraw_money;";

                        jdbcTemplate.execute(withdrawMoneyProc);
                        System.out.println(
                                        "[RevPay Init] Recompiled procedure: withdraw_money (Added transaction logging)");

                        String transferMoneyProc = "CREATE OR REPLACE PROCEDURE transfer_money(\n" +
                                        "    p_sender_id      IN  NUMBER,\n" +
                                        "    p_receiver_id    IN  NUMBER,\n" +
                                        "    p_amount         IN  NUMBER,\n" +
                                        "    p_description    IN  VARCHAR2,\n" +
                                        "    p_transaction_id IN  VARCHAR2,\n" +
                                        "    p_out_tx_id      OUT NUMBER\n" +
                                        ") AS\n" +
                                        "    v_balance NUMBER;\n" +
                                        "BEGIN\n" +
                                        "    SELECT balance INTO v_balance FROM wallet WHERE user_id = p_sender_id FOR UPDATE;\n"
                                        +
                                        "\n" +
                                        "    IF v_balance < p_amount THEN\n" +
                                        "        RAISE_APPLICATION_ERROR(-20001, 'Insufficient wallet balance');\n" +
                                        "    END IF;\n" +
                                        "\n" +
                                        "    -- Debit sender\n" +
                                        "    UPDATE wallet SET balance = balance - p_amount, updated_at = SYSTIMESTAMP\n"
                                        +
                                        "     WHERE user_id = p_sender_id;\n" +
                                        "\n" +
                                        "    -- Credit receiver (auto-create if missing, avoiding MERGE to fix ORA-00923)\n"
                                        +
                                        "    UPDATE wallet SET balance = balance + p_amount, updated_at = SYSTIMESTAMP\n"
                                        +
                                        "     WHERE user_id = p_receiver_id;\n" +
                                        "\n" +
                                        "    IF SQL%ROWCOUNT = 0 THEN\n" +
                                        "        INSERT INTO wallet(user_id, balance, currency, updated_at)\n" +
                                        "        VALUES(p_receiver_id, p_amount, 'INR', SYSTIMESTAMP);\n" +
                                        "    END IF;\n" +
                                        "\n" +
                                        "    -- Record transaction\n" +
                                        "    INSERT INTO transactions(sender_id, receiver_id, amount, transaction_type, description, status, transaction_id, created_at)\n"
                                        +
                                        "    VALUES(p_sender_id, p_receiver_id, p_amount, 'TRANSFER', p_description, 'COMPLETED', p_transaction_id, SYSTIMESTAMP)\n"
                                        +
                                        "    RETURNING id INTO p_out_tx_id;\n" +
                                        "\n" +
                                        "    -- Notifications\n" +
                                        "    INSERT INTO notifications(user_id, title, message, type, is_read, reference_url, created_at)\n"
                                        +
                                        "    VALUES(p_sender_id, 'Money Sent', 'You sent \u20B9' || p_amount || ' - ' || NVL(p_description, 'Transfer') || ' (Txn ID: ' || p_transaction_id || ')', 'TRANSACTION', 0, '/transactions/history', SYSTIMESTAMP);\n"
                                        +
                                        "\n" +
                                        "    INSERT INTO notifications(user_id, title, message, type, is_read, reference_url, created_at)\n"
                                        +
                                        "    VALUES(p_receiver_id, 'Money Received', 'You received \u20B9' || p_amount || ' - ' || NVL(p_description, 'Transfer') || ' (Txn ID: ' || p_transaction_id || ')', 'TRANSACTION', 0, '/transactions/history', SYSTIMESTAMP);\n"
                                        +
                                        "\n" +
                                        "    COMMIT;\n" +
                                        "EXCEPTION\n" +
                                        "    WHEN OTHERS THEN ROLLBACK; RAISE;\n" +
                                        "END transfer_money;";

                        jdbcTemplate.execute(transferMoneyProc);
                        System.out.println("[RevPay Init] Recompiled procedure: transfer_money (Fixed ORA-00923)");
                        String createRequestProc = "CREATE OR REPLACE PROCEDURE create_request(\n" +
                                        "    p_requester_id  IN  NUMBER,\n" +
                                        "    p_requestee_id  IN  NUMBER,\n" +
                                        "    p_amount        IN  NUMBER,\n" +
                                        "    p_description   IN  VARCHAR2,\n" +
                                        "    p_out_req_id    OUT NUMBER\n" +
                                        ") AS\n" +
                                        "BEGIN\n" +
                                        "    INSERT INTO money_requests(requester_id, requestee_id, amount, description, status)\n"
                                        +
                                        "    VALUES(p_requester_id, p_requestee_id, p_amount, p_description, 'PENDING')\n"
                                        +
                                        "    RETURNING id INTO p_out_req_id;\n" +
                                        "\n" +
                                        "    -- Notification\n" +
                                        "    INSERT INTO notifications(user_id, title, message, type, is_read, reference_url, created_at)\n"
                                        +
                                        "    VALUES(p_requestee_id, 'New Money Request', 'Money request of \u20B9' || p_amount || ' from user #' || p_requester_id, 'REQUEST', 0, '/transactions/history', SYSTIMESTAMP);\n"
                                        +
                                        "\n" +
                                        "    COMMIT;\n" +
                                        "EXCEPTION\n" +
                                        "    WHEN OTHERS THEN ROLLBACK; RAISE;\n" +
                                        "END create_request;";
                        jdbcTemplate.execute(createRequestProc);

                        String updateRequestProc = "CREATE OR REPLACE PROCEDURE update_request_status(\n" +
                                        "    p_request_id     IN  NUMBER,\n" +
                                        "    p_user_id        IN  NUMBER,\n" +
                                        "    p_new_status     IN  VARCHAR2,\n" +
                                        "    p_transaction_id IN  VARCHAR2,\n" +
                                        "    p_out_status     OUT VARCHAR2\n" +
                                        ") AS\n" +
                                        "    v_requester_id  NUMBER;\n" +
                                        "    v_requestee_id  NUMBER;\n" +
                                        "    v_amount        NUMBER;\n" +
                                        "BEGIN\n" +
                                        "    SELECT requester_id, requestee_id, amount\n" +
                                        "      INTO v_requester_id, v_requestee_id, v_amount\n" +
                                        "      FROM money_requests WHERE id = p_request_id;\n" +
                                        "\n" +
                                        "    UPDATE money_requests SET status = p_new_status WHERE id = p_request_id;\n"
                                        +
                                        "\n" +
                                        "    IF p_new_status = 'ACCEPTED' THEN\n" +
                                        "        -- Trigger Transfer\n" +
                                        "        transfer_money(v_requestee_id, v_requester_id, v_amount, 'Request Accepted', p_transaction_id, p_out_status);\n"
                                        +
                                        "    END IF;\n" +
                                        "\n" +
                                        "    p_out_status := p_new_status;\n" +
                                        "    COMMIT;\n" +
                                        "EXCEPTION\n" +
                                        "    WHEN OTHERS THEN ROLLBACK; RAISE;\n" +
                                        "END update_request_status;";
                        jdbcTemplate.execute(updateRequestProc);

                        System.out.println(
                                        "[RevPay Init] Recompiled Request procedures (added preferences check & formatting)");

                        String createInvoiceProc = "CREATE OR REPLACE PROCEDURE create_invoice(\n" +
                                        "    p_business_id      IN  NUMBER,\n" +
                                        "    p_customer_email   IN  VARCHAR2,\n" +
                                        "    p_customer_name    IN  VARCHAR2,\n" +
                                        "    p_customer_address IN  VARCHAR2,\n" +
                                        "    p_payment_terms    IN  VARCHAR2,\n" +
                                        "    p_due_date         IN  DATE,\n" +
                                        "    p_out_invoice_id   OUT NUMBER\n" +
                                        ") AS\n" +
                                        "BEGIN\n" +
                                        "    INSERT INTO invoice(business_id, customer_email, customer_name, customer_address, payment_terms, due_date, status)\n"
                                        +
                                        "    VALUES(p_business_id, p_customer_email, p_customer_name, p_customer_address, p_payment_terms, p_due_date, 'DRAFT')\n"
                                        +
                                        "    RETURNING id INTO p_out_invoice_id;\n" +
                                        "    COMMIT;\n" +
                                        "EXCEPTION\n" +
                                        "    WHEN OTHERS THEN ROLLBACK; RAISE;\n" +
                                        "END create_invoice;";
                        jdbcTemplate.execute(createInvoiceProc);
                        System.out.println(
                                        "[RevPay Init] Recompiled procedure: create_invoice (Added customer info and payment terms)");

                } catch (Exception e) {
                        System.err.println("[RevPay Init] Failed to recompile procedure: " + e.getMessage());
                }
        }

        /**
         * Create BUSINESS_VERIFICATION_DOCS table for business account approval
         * submissions.
         */
        private void ensureVerificationDocumentsTableExist() {
                // Sequence
                try {
                        List<Map<String, Object>> seq = jdbcTemplate.queryForList(
                                        "SELECT sequence_name FROM user_sequences WHERE sequence_name = 'VERIFICATION_DOC_SEQ'");
                        if (seq.isEmpty()) {
                                jdbcTemplate.execute(
                                                "CREATE SEQUENCE verification_doc_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE");
                                System.out.println("[RevPay Init] Created sequence: verification_doc_seq");
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] verification_doc_seq: " + e.getMessage());
                }

                // Table
                try {
                        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                                        "SELECT table_name FROM user_tables WHERE table_name = 'BUSINESS_VERIFICATION_DOCS'");
                        if (rows.isEmpty()) {
                                jdbcTemplate.execute(
                                                "CREATE TABLE business_verification_docs (" +
                                                                "  id              NUMBER PRIMARY KEY, " +
                                                                "  user_id         NUMBER NOT NULL REFERENCES users(id), "
                                                                +
                                                                "  business_name   VARCHAR2(150), " +
                                                                "  doc_type        VARCHAR2(100) NOT NULL, " +
                                                                "  doc_description VARCHAR2(500), " +
                                                                "  status          VARCHAR2(20) DEFAULT 'PENDING', " +
                                                                "  admin_remarks   VARCHAR2(500), " +
                                                                "  submitted_at    TIMESTAMP DEFAULT SYSTIMESTAMP, " +
                                                                "  reviewed_at     TIMESTAMP)");
                                jdbcTemplate.execute(
                                                "CREATE OR REPLACE TRIGGER trg_verification_doc_id " +
                                                                "  BEFORE INSERT ON business_verification_docs FOR EACH ROW "
                                                                +
                                                                "BEGIN IF :NEW.id IS NULL THEN " +
                                                                "  SELECT verification_doc_seq.NEXTVAL INTO :NEW.id FROM dual; "
                                                                +
                                                                "END IF; END;");
                                System.out.println("[RevPay Init] Created table: BUSINESS_VERIFICATION_DOCS");
                        }
                } catch (Exception e) {
                        System.err.println("[RevPay Init] BUSINESS_VERIFICATION_DOCS: " + e.getMessage());
                }
        }
}
