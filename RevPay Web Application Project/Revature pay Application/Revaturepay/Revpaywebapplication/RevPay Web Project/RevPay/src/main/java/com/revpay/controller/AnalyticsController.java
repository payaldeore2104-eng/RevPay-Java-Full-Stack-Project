package com.revpay.controller;

import com.revpay.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;

@Controller
@RequestMapping("/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String getLoggedInLoginId(HttpSession session) {
        Object user = session.getAttribute("loggedInUser");
        return (user != null) ? user.toString() : null;
    }

    private Long resolveUserId(String loginId) {
        try {
            java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE email = ? OR phone = ?", loginId, loginId);
            if (rows.isEmpty())
                return null;
            return ((Number) rows.get(0).get("ID")).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveUserName(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return "User";
        }
    }

    @GetMapping("")
    public String analyticsDashboard(HttpSession session, Model model) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";

        // User identity
        model.addAttribute("user", resolveUserName(userId));

        // Summary stats for analytics overview
        BigDecimal totalReceived = analyticsService.getMonthlyReceived(userId);
        BigDecimal totalSent = analyticsService.getMonthlySent(userId);

        // Pending = transactions sent but not yet COMPLETED
        BigDecimal pendingAmount = BigDecimal.ZERO;
        try {
            BigDecimal p = jdbcTemplate.queryForObject(
                    "SELECT NVL(SUM(amount), 0) FROM transactions " +
                            "WHERE sender_id = ? AND status = 'PENDING'",
                    BigDecimal.class, userId);
            pendingAmount = p != null ? p : BigDecimal.ZERO;
        } catch (Exception ignored) {
        }

        model.addAttribute("totalReceived", totalReceived);
        model.addAttribute("totalSent", totalSent);
        model.addAttribute("pendingAmount", pendingAmount);

        return "analytics-dashboard";
    }

    @GetMapping("/spending")
    public String spendingAnalytics(HttpSession session, Model model) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";

        // User identity
        model.addAttribute("user", resolveUserName(userId));

        // Monthly stats
        BigDecimal monthlySent = analyticsService.getMonthlySent(userId);
        BigDecimal monthlyReceived = analyticsService.getMonthlyReceived(userId);
        BigDecimal netChange = monthlyReceived.subtract(monthlySent);

        model.addAttribute("monthlySent", monthlySent);
        model.addAttribute("monthlyReceived", monthlyReceived);
        model.addAttribute("netChange", netChange);
        model.addAttribute("netChangePositive", netChange.compareTo(BigDecimal.ZERO) >= 0);

        // Category spending
        model.addAttribute("categoryStats", analyticsService.getCategorySpending(userId));

        // Top payees
        model.addAttribute("topPayees", analyticsService.getTopPayees(userId));

        // Weekly spending trend
        model.addAttribute("weeklyTrend", analyticsService.getWeeklyTrend(userId));

        // Monthly trend
        model.addAttribute("monthlyTrend", analyticsService.getMonthlyTrend(userId));

        // Total transaction count
        model.addAttribute("totalTxCount", analyticsService.getTotalTransactionCount(userId));

        return "spending-analytics";
    }
}
