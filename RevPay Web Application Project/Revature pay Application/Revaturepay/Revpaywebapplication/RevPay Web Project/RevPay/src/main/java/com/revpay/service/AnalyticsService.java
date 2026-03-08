package com.revpay.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class AnalyticsService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── 1. Monthly Sent ──────────────────────────────────────────────────────
    public BigDecimal getMonthlySent(Long userId) {
        try {
            BigDecimal result = jdbcTemplate.queryForObject(
                    "SELECT NVL(SUM(t.amount), 0) FROM transactions t " +
                            "WHERE t.sender_id = ? " +
                            "AND EXTRACT(MONTH FROM t.created_at) = EXTRACT(MONTH FROM SYSDATE) " +
                            "AND EXTRACT(YEAR FROM t.created_at) = EXTRACT(YEAR FROM SYSDATE) " +
                            "AND t.status = 'COMPLETED'",
                    BigDecimal.class, userId);
            return result != null ? result : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ── 2. Monthly Received ──────────────────────────────────────────────────
    public BigDecimal getMonthlyReceived(Long userId) {
        try {
            BigDecimal result = jdbcTemplate.queryForObject(
                    "SELECT NVL(SUM(t.amount), 0) FROM transactions t " +
                            "WHERE t.receiver_id = ? " +
                            "AND EXTRACT(MONTH FROM t.created_at) = EXTRACT(MONTH FROM SYSDATE) " +
                            "AND EXTRACT(YEAR FROM t.created_at) = EXTRACT(YEAR FROM SYSDATE) " +
                            "AND t.status = 'COMPLETED'",
                    BigDecimal.class, userId);
            return result != null ? result : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ── 3. Category-Based Spending (derived from description / transaction_type)
    // ──
    public List<Map<String, Object>> getCategorySpending(Long userId) {
        try {
            // Derive categories using CASE expression on description keywords
            String sql = "SELECT category, NVL(SUM(amount), 0) AS total " +
                    "FROM ( " +
                    "  SELECT t.amount, " +
                    "    CASE " +
                    "      WHEN t.transaction_type = 'WITHDRAWAL' THEN 'Bank Withdrawal' " +
                    "      WHEN LOWER(NVL(t.description,'')) LIKE '%food%' OR LOWER(NVL(t.description,'')) LIKE '%restaurant%' OR LOWER(NVL(t.description,'')) LIKE '%eat%' OR LOWER(NVL(t.description,'')) LIKE '%lunch%' OR LOWER(NVL(t.description,'')) LIKE '%dinner%' OR LOWER(NVL(t.description,'')) LIKE '%breakfast%' THEN 'Food & Dining'"
                    +
                    "      WHEN LOWER(NVL(t.description,'')) LIKE '%shop%' OR LOWER(NVL(t.description,'')) LIKE '%buy%' OR LOWER(NVL(t.description,'')) LIKE '%purchase%' OR LOWER(NVL(t.description,'')) LIKE '%amazon%' OR LOWER(NVL(t.description,'')) LIKE '%flipkart%' THEN 'Shopping'"
                    +
                    "      WHEN LOWER(NVL(t.description,'')) LIKE '%bill%' OR LOWER(NVL(t.description,'')) LIKE '%electric%' OR LOWER(NVL(t.description,'')) LIKE '%water%' OR LOWER(NVL(t.description,'')) LIKE '%gas%' OR LOWER(NVL(t.description,'')) LIKE '%phone%' OR LOWER(NVL(t.description,'')) LIKE '%internet%' OR LOWER(NVL(t.description,'')) LIKE '%rent%' THEN 'Bills & Utilities'"
                    +
                    "      WHEN LOWER(NVL(t.description,'')) LIKE '%travel%' OR LOWER(NVL(t.description,'')) LIKE '%hotel%' OR LOWER(NVL(t.description,'')) LIKE '%flight%' OR LOWER(NVL(t.description,'')) LIKE '%cab%' OR LOWER(NVL(t.description,'')) LIKE '%uber%' OR LOWER(NVL(t.description,'')) LIKE '%ola%' THEN 'Travel'"
                    +
                    "      WHEN LOWER(NVL(t.description,'')) LIKE '%party%' OR LOWER(NVL(t.description,'')) LIKE '%fun%' OR LOWER(NVL(t.description,'')) LIKE '%movie%' OR LOWER(NVL(t.description,'')) LIKE '%entertainment%' THEN 'Entertainment'"
                    +
                    "      WHEN LOWER(NVL(t.description,'')) LIKE '%salary%' OR LOWER(NVL(t.description,'')) LIKE '%payment%' OR LOWER(NVL(t.description,'')) LIKE '%invoice%' THEN 'Payment'"
                    +
                    "      WHEN t.transaction_type = 'TRANSFER' THEN 'Personal Transfer' " +
                    "      ELSE 'Other' " +
                    "    END AS category " +
                    "  FROM transactions t " +
                    "  WHERE t.sender_id = ? AND t.status = 'COMPLETED' " +
                    ") GROUP BY category ORDER BY total DESC";

            return jdbcTemplate.queryForList(sql, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── 4. Top Frequent Payees ───────────────────────────────────────────────
    public List<Map<String, Object>> getTopPayees(Long userId) {
        try {
            String sql = "SELECT u.full_name AS payee_name, u.email AS payee_email, COUNT(*) AS tx_count, SUM(t.amount) AS total_sent "
                    +
                    "FROM transactions t " +
                    "JOIN users u ON t.receiver_id = u.id " +
                    "WHERE t.sender_id = ? AND t.status = 'COMPLETED' " +
                    "GROUP BY u.id, u.full_name, u.email " +
                    "ORDER BY tx_count DESC " +
                    "FETCH FIRST 5 ROWS ONLY";
            return jdbcTemplate.queryForList(sql, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── 5. Weekly Spending Trend (last 4 weeks) ──────────────────────────────
    public List<Map<String, Object>> getWeeklyTrend(Long userId) {
        try {
            String sql = "SELECT week_label, NVL(SUM(amount), 0) AS total " +
                    "FROM ( " +
                    "  SELECT t.amount, " +
                    "    CASE " +
                    "      WHEN t.created_at >= SYSDATE - 7  THEN 'This Week' " +
                    "      WHEN t.created_at >= SYSDATE - 14 THEN 'Last Week' " +
                    "      WHEN t.created_at >= SYSDATE - 21 THEN '2 Weeks Ago' " +
                    "      WHEN t.created_at >= SYSDATE - 28 THEN '3 Weeks Ago' " +
                    "      ELSE NULL " +
                    "    END AS week_label " +
                    "  FROM transactions t " +
                    "  WHERE t.sender_id = ? AND t.status = 'COMPLETED' " +
                    "    AND t.created_at >= SYSDATE - 28 " +
                    ") WHERE week_label IS NOT NULL " +
                    "GROUP BY week_label " +
                    "ORDER BY CASE week_label " +
                    "  WHEN '3 Weeks Ago' THEN 1 " +
                    "  WHEN '2 Weeks Ago' THEN 2 " +
                    "  WHEN 'Last Week' THEN 3 " +
                    "  WHEN 'This Week' THEN 4 END";
            return jdbcTemplate.queryForList(sql, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── 6. Monthly Trend (last 6 months) ────────────────────────────────────
    public List<Map<String, Object>> getMonthlyTrend(Long userId) {
        try {
            String sql = "SELECT TO_CHAR(t.created_at, 'Mon YYYY') AS month_label, " +
                    "       EXTRACT(YEAR FROM t.created_at) AS yr, " +
                    "       EXTRACT(MONTH FROM t.created_at) AS mo, " +
                    "       NVL(SUM(t.amount), 0) AS total " +
                    "FROM transactions t " +
                    "WHERE t.sender_id = ? AND t.status = 'COMPLETED' " +
                    "  AND t.created_at >= ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -5) " +
                    "GROUP BY TO_CHAR(t.created_at, 'Mon YYYY'), " +
                    "         EXTRACT(YEAR FROM t.created_at), " +
                    "         EXTRACT(MONTH FROM t.created_at) " +
                    "ORDER BY yr, mo";
            return jdbcTemplate.queryForList(sql, userId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── 7. Total Transaction Count ───────────────────────────────────────────
    public int getTotalTransactionCount(Long userId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM transactions WHERE (sender_id = ? OR receiver_id = ?) AND status = 'COMPLETED'",
                    Integer.class, userId, userId);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

}
