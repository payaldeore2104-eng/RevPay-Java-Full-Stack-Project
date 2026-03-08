package com.revpay.controller;

import com.revpay.model.Notification;
import com.revpay.model.User;
import com.revpay.repository.NotificationRepository;
import com.revpay.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User getLoggedInUser(HttpSession session) {
        String loginId = (String) session.getAttribute("loggedInUser");
        if (loginId == null)
            return null;
        try {
            return userRepository.findByEmail(loginId)
                    .orElseGet(() -> {
                        try {
                            return userRepository.findByPhone(loginId).orElse(null);
                        } catch (Exception ex) {
                            return null;
                        }
                    });
        } catch (Exception e) {
            // DB unavailable – return stub so session is still honoured
            User stub = new User();
            stub.setEmail(loginId);
            return stub;
        }
    }

    @GetMapping
    public String viewNotifications(@RequestParam(value = "category", required = false) String category,
            HttpSession session, Model model) {
        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";
        try {
            List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

            // Filter by category in-memory for simplicity
            if (category != null && !category.isEmpty() && !category.equalsIgnoreCase("ALL")) {
                notifications = notifications.stream()
                        .filter(n -> category.equalsIgnoreCase(n.getType()))
                        .collect(Collectors.toList());
            }

            model.addAttribute("notifications", notifications);
            long unreadCount = notifications.stream().filter(n -> n.getIsRead() == 0).count();
            model.addAttribute("unreadCount", unreadCount);
            model.addAttribute("currentCategory", category == null ? "ALL" : category.toUpperCase());
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("notifications", java.util.Collections.emptyList());
            model.addAttribute("unreadCount", 0L);
            model.addAttribute("currentCategory", "ALL");
            model.addAttribute("error", "Could not load notifications: " + e.getMessage());
        }
        return "notifications";
    }

    @PostMapping("/mark-read")
    @Transactional
    public String markAllAsRead(HttpSession session) {
        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";

        notificationRepository.markAllAsRead(user.getId());
        return "redirect:/notifications";
    }

    @PostMapping("/toggle-read")
    @Transactional
    public String toggleRead(@RequestParam("id") Long id,
            @RequestParam(value = "category", required = false) String category, HttpSession session) {
        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";

        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification != null && notification.getUser().getId().equals(user.getId())) {
            int newVal = notification.getIsRead() == 0 ? 1 : 0;
            // Use JDBC to avoid Hibernate null-title validation on legacy rows
            jdbcTemplate.update("UPDATE notifications SET is_read = ? WHERE id = ?", newVal, id);
        }

        String redirectUrl = "/notifications";
        if (category != null && !category.isEmpty()) {
            redirectUrl += "?category=" + category;
        }
        return "redirect:" + redirectUrl;
    }

    /**
     * Open a notification: mark it as read, then redirect to the relevant page.
     * If the notification does not belong to the logged-in user, fall back to
     * /notifications.
     */
    @GetMapping("/open/{id}")
    @Transactional
    public String openNotification(@PathVariable("id") Long id, HttpSession session) {
        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";

        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null || !notification.getUser().getId().equals(user.getId())) {
            return "redirect:/notifications";
        }

        // Mark as read — use JDBC to avoid Hibernate null-title validation on legacy
        // rows
        if (notification.getIsRead() == 0) {
            jdbcTemplate.update("UPDATE notifications SET is_read = 1 WHERE id = ?", id);
        }

        // Priority 1: use stored deep-link URL
        String refUrl = notification.getReferenceUrl();
        if (refUrl != null && !refUrl.isBlank()) {
            if (refUrl.startsWith("/business/invoices/")) {
                // Dynamically rewrite legacy DB URLs to the new controller
                refUrl = refUrl.replace("/business/invoices/", "/invoices/");
            }
            return "redirect:" + refUrl;
        }

        // Priority 2: keyword-based fallback for older notifications (no stored URL).
        // IMPORTANT: invoice check must come BEFORE the transaction check because
        // invoice messages contain the word "received" (e.g. "You have received a new
        // invoice…"), which would otherwise be caught by the TRANSACTION branch first.
        String type = notification.getType() != null ? notification.getType().toUpperCase() : "";
        String msg = notification.getMessage() != null ? notification.getMessage().toLowerCase() : "";
        String title = notification.getTitle() != null ? notification.getTitle().toLowerCase() : "";

        if (msg.contains("invoice") || title.contains("invoice")) {
            // Old invoice notifications without a stored URL.
            // Business users → their dashboard; regular customers → personal dashboard
            // (customers cannot access /business/dashboard – it requires ROLE_BUSINESS).
            try {
                String role = jdbcTemplate.queryForObject(
                        "SELECT role FROM users WHERE id = ?", String.class, user.getId());
                if ("ROLE_BUSINESS".equals(role) || "ROLE_ADMIN".equals(role)) {
                    return "redirect:/business/dashboard";
                }
            } catch (Exception ignored) {
            }
            return "redirect:/dashboard";
        } else if (type.equals("TRANSACTION") || msg.contains("sent") || msg.contains("received")
                || msg.contains("transfer") || msg.contains("deposit") || msg.contains("withdraw")) {
            return "redirect:/transactions/history";
        } else if (type.equals("REQUEST") || msg.contains("request") || title.contains("request")) {
            return "redirect:/transactions/history";
        } else if (msg.contains("loan") || title.contains("loan")) {
            return "redirect:/loans";
        } else if (msg.contains("card") || msg.contains("bank account") || msg.contains("wallet")) {
            return "redirect:/wallet";
        } else {
            return "redirect:/dashboard";
        }
    }
}
