package com.revpay.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void sendNotification(Long userId, String title, String message, String type) {
        sendNotification(userId, title, message, type, null);
    }

    @Override
    public void sendNotification(Long userId, String title, String message, String type, String referenceUrl) {
        try {
            java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT pref_notif_transactions, pref_notif_requests, pref_notif_alerts " +
                            "FROM users WHERE id = ?",
                    userId);

            if (rows.isEmpty()) {
                return;
            }

            java.util.Map<String, Object> prefs = rows.get(0);

            boolean shouldSend = false;
            if ("TRANSACTION".equalsIgnoreCase(type)) {
                Object val = prefs.get("PREF_NOTIF_TRANSACTIONS");
                shouldSend = (val != null && ((Number) val).intValue() == 1);
            } else if ("REQUEST".equalsIgnoreCase(type)) {
                Object val = prefs.get("PREF_NOTIF_REQUESTS");
                shouldSend = (val != null && ((Number) val).intValue() == 1);
            } else if ("ALERT".equalsIgnoreCase(type)) {
                Object val = prefs.get("PREF_NOTIF_ALERTS");
                shouldSend = (val != null && ((Number) val).intValue() == 1);
            } else {
                shouldSend = true;
            }

            if (shouldSend) {
                jdbcTemplate.update(
                        "INSERT INTO notifications (id, user_id, title, message, type, is_read, reference_url, created_at) "
                                +
                                "VALUES (notification_seq.NEXTVAL, ?, ?, ?, ?, 0, ?, SYSTIMESTAMP)",
                        userId, title, message, type.toUpperCase(), referenceUrl);
            }
        } catch (Exception e) {
            System.err.println(
                    "[NotificationService] Failed to send notification to user " + userId + ": " + e.getMessage());
        }
    }
}
