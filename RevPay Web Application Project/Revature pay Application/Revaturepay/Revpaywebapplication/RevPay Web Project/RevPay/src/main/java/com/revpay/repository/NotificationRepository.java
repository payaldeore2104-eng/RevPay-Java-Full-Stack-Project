package com.revpay.repository;

import com.revpay.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = 1 WHERE n.user.id = ?1")
    void markAllAsRead(Long userId);
}
