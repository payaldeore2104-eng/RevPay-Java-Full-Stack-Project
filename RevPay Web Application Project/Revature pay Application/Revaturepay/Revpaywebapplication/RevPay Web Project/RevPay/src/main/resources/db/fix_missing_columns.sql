

-- Add 'title' column to notifications (if not exists)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE notifications ADD (title VARCHAR2(100))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN NULL; -- Column already exists, skip
        ELSE RAISE;
        END IF;
END;
/

-- Add 'type' column to notifications (if not exists)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE notifications ADD (type VARCHAR2(50) DEFAULT ''ALERT'')';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN NULL; -- Column already exists, skip
        ELSE RAISE;
        END IF;
END;
/

-- Add 'updated_at' column to money_requests (if not exists)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE money_requests ADD (updated_at TIMESTAMP)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN NULL; -- Column already exists, skip
        ELSE RAISE;
        END IF;
END;
/

-- Add 'pref_notif_transactions' column to users (if not exists)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (pref_notif_transactions NUMBER(1) DEFAULT 1)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN NULL; -- Column already exists, skip
        ELSE RAISE;
        END IF;
END;
/

-- Add 'pref_notif_requests' column to users (if not exists)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (pref_notif_requests NUMBER(1) DEFAULT 1)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN NULL; -- Column already exists, skip
        ELSE RAISE;
        END IF;
END;
/

-- Add 'pref_notif_alerts' column to users (if not exists)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (pref_notif_alerts NUMBER(1) DEFAULT 1)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -1430 THEN NULL; -- Column already exists, skip
        ELSE RAISE;
        END IF;
END;
/

-- Verify
SELECT column_name, data_type, data_length
FROM user_tab_columns
WHERE table_name IN ('NOTIFICATIONS', 'MONEY_REQUESTS', 'USERS')
ORDER BY table_name, column_id;
