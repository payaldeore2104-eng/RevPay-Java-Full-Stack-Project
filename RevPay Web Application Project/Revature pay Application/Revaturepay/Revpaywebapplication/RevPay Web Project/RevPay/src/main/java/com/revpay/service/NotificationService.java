package com.revpay.service;

public interface NotificationService {

    /** Send a notification without a specific deep-link URL. */
    void sendNotification(Long userId, String title, String message, String type);

    /**
     * Send a notification with a deep-link URL to the specific item.
     * When the user clicks the notification, they will be redirected to
     * referenceUrl.
     */
    void sendNotification(Long userId, String title, String message, String type, String referenceUrl);
}
