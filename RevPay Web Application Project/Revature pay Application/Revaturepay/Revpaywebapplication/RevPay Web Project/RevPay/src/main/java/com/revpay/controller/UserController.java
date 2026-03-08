package com.revpay.controller;

import com.revpay.dto.ProfileUpdateDto;
import com.revpay.model.User;
import com.revpay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Helper to get logged-in user — uses JDBC-based lookup to avoid JPA schema
    // issues
    private User getLoggedInUser(HttpSession session) {
        String loginId = (String) session.getAttribute("loggedInUser");
        if (loginId == null)
            return null;
        return userService.getUserByLoginId(loginId);
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        if (session.getAttribute("loggedInUser") != null) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        String loginId = (String) session.getAttribute("loggedInUser");
        if (loginId == null)
            return "redirect:/login";

        // Pass role for conditional sidebar rendering
        try {
            String role = jdbcTemplate.queryForObject(
                    "SELECT role FROM users WHERE email = ? OR phone = ?",
                    String.class, loginId, loginId);
            model.addAttribute("userRole", role != null ? role : "");
        } catch (Exception e) {
            model.addAttribute("userRole", "");
        }
        // Surface access_denied error from business redirect
        if ("access_denied".equals(session.getAttribute("error"))) {
            model.addAttribute("error", "Access denied: Business accounts only.");
            session.removeAttribute("error");
        }

        User user = getLoggedInUser(session);
        if (user != null) {
            model.addAttribute("user", user.getFullName());
            model.addAttribute("userId", user.getId());
            // Fetch Wallet Balance
            try {
                java.math.BigDecimal balance = jdbcTemplate.queryForObject(
                        "SELECT balance FROM wallet WHERE user_id = ?",
                        java.math.BigDecimal.class, user.getId());
                model.addAttribute("balance", balance);
            } catch (Exception e) {
                model.addAttribute("balance", new java.math.BigDecimal("0.00"));
            }

            // Fetch Top 5 Recent Transactions
            try {
                java.util.List<java.util.Map<String, Object>> transactions = jdbcTemplate.queryForList(
                        "SELECT * FROM transactions WHERE sender_id = ? OR receiver_id = ? ORDER BY created_at DESC FETCH FIRST 5 ROWS ONLY",
                        user.getId(), user.getId());
                model.addAttribute("transactions", transactions);
            } catch (Exception e) {
                model.addAttribute("transactions", java.util.Collections.emptyList());
            }

            // Fetch Top 5 Notifications
            try {
                java.util.List<java.util.Map<String, Object>> notifications = jdbcTemplate.queryForList(
                        "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC FETCH FIRST 5 ROWS ONLY",
                        user.getId());
                model.addAttribute("notifications", notifications);
            } catch (Exception e) {
                model.addAttribute("notifications", java.util.Collections.emptyList());
            }

            // Fetch pending invoices for this user
            try {
                java.util.List<java.util.Map<String, Object>> pendingInvoices = jdbcTemplate.queryForList(
                        "SELECT i.*, u.business_name FROM invoice i JOIN users u ON i.business_id = u.id " +
                                "WHERE (i.customer_email = ? OR i.customer_email = ? OR i.customer_email = CAST(? AS VARCHAR2(100))) "
                                +
                                "AND i.status = 'SENT' ORDER BY i.created_at DESC",
                        user.getEmail(), user.getPhone(), user.getId());
                model.addAttribute("pendingInvoices", pendingInvoices);
            } catch (Exception e) {
                model.addAttribute("pendingInvoices", java.util.Collections.emptyList());
            }
        }

        return "dashboard";
    }

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        User user = getLoggedInUser(session);
        if (user == null)
            return "redirect:/login";

        // Pass the actual user object
        model.addAttribute("user", user);

        try {
            java.util.List<java.util.Map<String, Object>> questions = jdbcTemplate
                    .queryForList("SELECT id, question FROM security_questions ORDER BY id");
            model.addAttribute("questions", questions);
        } catch (Exception e) {
        }

        return "profile";
    }

    @PostMapping("/profile/setup-security")
    public String setupSecurity(@RequestParam("q1Id") Long q1Id, @RequestParam("a1") String a1,
            @RequestParam("q2Id") Long q2Id, @RequestParam("a2") String a2,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null)
            return "redirect:/login";

        java.util.List<com.revpay.dto.SecurityAnswerDto> answers = new java.util.ArrayList<>();
        com.revpay.dto.SecurityAnswerDto ans1 = new com.revpay.dto.SecurityAnswerDto();
        ans1.setQuestionId(q1Id);
        ans1.setAnswer(a1);
        com.revpay.dto.SecurityAnswerDto ans2 = new com.revpay.dto.SecurityAnswerDto();
        ans2.setQuestionId(q2Id);
        ans2.setAnswer(a2);
        answers.add(ans1);
        answers.add(ans2);

        try {
            userService.setupSecurityQuestions(user.getId(), answers);
            logger.info("Security questions successfully set up for User ID {}", user.getId());
            return "redirect:/profile?success=security";
        } catch (Exception e) {
            logger.error("Failed to set up security questions for User ID {}.", user.getId(), e);
            redirectAttributes.addFlashAttribute("error", "Could not set up security questions: " + e.getMessage());
            return "redirect:/profile";
        }
    }

    @PostMapping("/profile/update")
    public String updateProfile(@ModelAttribute ProfileUpdateDto dto, HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null)
            return "redirect:/login";

        try {
            userService.updateProfile(user.getId(), dto);
            // If they changed their email/phone used for login, update session
            if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
                session.setAttribute("loggedInUser", dto.getEmail());
            } else if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
                session.setAttribute("loggedInUser", dto.getPhone());
            }
            logger.info("Profile updated successfully for User ID {}", user.getId());
            return "redirect:/profile?success=profile";
        } catch (Exception e) {
            logger.error("Failed to update profile for User ID {}.", user.getId(), e);
            redirectAttributes.addFlashAttribute("error", "Could not update profile: " + e.getMessage());
            return "redirect:/profile";
        }
    }

    @PostMapping("/profile/change-password")
    public String changePassword(@RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null)
            return "redirect:/login";

        try {
            userService.changePassword(user.getId(), oldPassword, newPassword);
            return "redirect:/profile?success=password";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not change password: " + e.getMessage());
            return "redirect:/profile";
        }
    }

    @PostMapping("/profile/update-notifications")
    public String updateNotificationPreferences(
            @RequestParam(value = "prefNotifTransactions", required = false) String prefTransactionsStr,
            @RequestParam(value = "prefNotifRequests", required = false) String prefRequestsStr,
            @RequestParam(value = "prefNotifAlerts", required = false) String prefAlertsStr,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null)
            return "redirect:/login";

        Integer prefTransactions = prefTransactionsStr != null ? 1 : 0;
        Integer prefRequests = prefRequestsStr != null ? 1 : 0;
        Integer prefAlerts = prefAlertsStr != null ? 1 : 0;

        try {
            userService.updateNotificationPreferences(user.getId(), prefTransactions, prefRequests, prefAlerts);
            return "redirect:/profile?success=notifications";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Could not update Notification Preferences: " + e.getMessage());
            return "redirect:/profile";
        }
    }
}
