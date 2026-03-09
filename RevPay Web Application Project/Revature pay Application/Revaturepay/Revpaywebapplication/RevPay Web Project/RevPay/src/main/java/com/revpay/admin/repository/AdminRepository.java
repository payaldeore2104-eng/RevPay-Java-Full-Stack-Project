package com.revpay.admin.repository;

import com.revpay.admin.dto.AdminStatsDto;
import com.revpay.admin.dto.AdminLoanDto;
import com.revpay.admin.dto.AdminTransactionDto;
import com.revpay.admin.dto.AdminUserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class AdminRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminUserDto> findAllUsers(String searchQuery) {
        StringBuilder sql = new StringBuilder(
                "SELECT u.id, u.full_name, u.email, u.phone, u.role, u.is_verified, u.is_active, u.login_attempts, u.created_at, " +
                        "       w.balance " +
                        "  FROM users u " +
                        "  LEFT JOIN wallet w ON w.user_id = u.id " +
                        " WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            sql.append(" AND (LOWER(u.full_name) LIKE ? OR LOWER(u.email) LIKE ? OR u.phone LIKE ?) ");
            String q = "%" + searchQuery.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add("%" + searchQuery.trim() + "%");
        }

        sql.append(" ORDER BY u.created_at DESC ");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<AdminUserDto> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            AdminUserDto dto = new AdminUserDto();
            dto.setId(toLong(r.get("ID")));
            dto.setFullName(toString(r.get("FULL_NAME")));
            dto.setEmail(toString(r.get("EMAIL")));
            dto.setPhone(toString(r.get("PHONE")));
            dto.setRole(toString(r.get("ROLE")));
            dto.setIsVerified(toInteger(r.get("IS_VERIFIED")));
            dto.setBalance(toBigDecimal(r.get("BALANCE")));
            dto.setCreatedAt(toTimestampAsDate(r.get("CREATED_AT")));

            int isActive = toInteger(r.get("IS_ACTIVE")) != null ? toInteger(r.get("IS_ACTIVE")) : 0;
            int attempts = toInteger(r.get("LOGIN_ATTEMPTS")) != null ? toInteger(r.get("LOGIN_ATTEMPTS")) : 0;
            dto.setStatus((isActive == 0 || attempts >= 5) ? "BLOCKED" : "ACTIVE");

            out.add(dto);
        }
        return out;
    }

    public void setUserBlocked(Long userId, boolean blocked) {
        if (blocked) {
            jdbcTemplate.update("UPDATE users SET is_active = 0 WHERE id = ?", userId);
        } else {
            jdbcTemplate.update("UPDATE users SET is_active = 1, login_attempts = 0 WHERE id = ?", userId);
        }
    }

    public List<AdminTransactionDto> findAllTransactions(
            String searchQuery,
            String status,
            String type,
            String startDate,
            String endDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.transaction_id, t.transaction_ref_id, t.amount, t.description, t.status, t.created_at, t.transaction_type, " +
                        "       s.id AS sender_id, s.full_name AS sender_name, COALESCE(s.email, s.phone) AS sender_contact, " +
                        "       r.id AS receiver_id, r.full_name AS receiver_name, COALESCE(r.email, r.phone) AS receiver_contact " +
                        "  FROM transactions t " +
                        "  LEFT JOIN users s ON t.sender_id = s.id " +
                        "  LEFT JOIN users r ON t.receiver_id = r.id " +
                        " WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty()) {
            sql.append(" AND UPPER(t.status) = ? ");
            params.add(status.trim().toUpperCase());
        }
        if (type != null && !type.trim().isEmpty()) {
            sql.append(" AND UPPER(t.transaction_type) = ? ");
            params.add(type.trim().toUpperCase());
        }
        if (startDate != null && !startDate.trim().isEmpty()) {
            sql.append(" AND TRUNC(t.created_at) >= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(startDate.trim());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            sql.append(" AND TRUNC(t.created_at) <= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(endDate.trim());
        }
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            sql.append(" AND ( " +
                    "       LOWER(t.transaction_id) LIKE ? " +
                    "    OR LOWER(NVL(t.transaction_ref_id, '')) LIKE ? " +
                    "    OR LOWER(NVL(s.full_name, '')) LIKE ? " +
                    "    OR LOWER(NVL(r.full_name, '')) LIKE ? " +
                    "    OR LOWER(NVL(s.email, '')) LIKE ? " +
                    "    OR LOWER(NVL(r.email, '')) LIKE ? " +
                    "    OR NVL(s.phone, '') LIKE ? " +
                    "    OR NVL(r.phone, '') LIKE ? " +
                    "    OR LOWER(NVL(t.description, '')) LIKE ? " +
                    " ) ");
            String q = "%" + searchQuery.trim().toLowerCase() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add("%" + searchQuery.trim() + "%");
            params.add("%" + searchQuery.trim() + "%");
            params.add(q);
        }

        sql.append(" ORDER BY t.created_at DESC FETCH FIRST 500 ROWS ONLY ");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<AdminTransactionDto> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            AdminTransactionDto dto = new AdminTransactionDto();
            dto.setId(toLong(r.get("ID")));
            dto.setTransactionId(toString(r.get("TRANSACTION_ID")));
            dto.setTransactionRefId(toString(r.get("TRANSACTION_REF_ID")));
            dto.setAmount(toBigDecimal(r.get("AMOUNT")));
            dto.setDescription(toString(r.get("DESCRIPTION")));
            dto.setStatus(toString(r.get("STATUS")));
            dto.setTransactionType(toString(r.get("TRANSACTION_TYPE")));
            dto.setCreatedAt(toTimestampAsDate(r.get("CREATED_AT")));

            dto.setSenderId(toLong(r.get("SENDER_ID")));
            dto.setSenderName(toString(r.get("SENDER_NAME")));
            dto.setSenderContact(toString(r.get("SENDER_CONTACT")));

            dto.setReceiverId(toLong(r.get("RECEIVER_ID")));
            dto.setReceiverName(toString(r.get("RECEIVER_NAME")));
            dto.setReceiverContact(toString(r.get("RECEIVER_CONTACT")));

            out.add(dto);
        }
        return out;
    }

    public AdminStatsDto getSystemStats() {
        AdminStatsDto dto = new AdminStatsDto();
        Long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        Long totalTransactions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Long.class);
        BigDecimal totalMoney = null;
        try {
            totalMoney = jdbcTemplate.queryForObject(
                    "SELECT SUM(amount) FROM transactions WHERE UPPER(status) = 'COMPLETED'",
                    BigDecimal.class);
        } catch (Exception ignored) {
        }
        Long activeUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_active = 1 AND NVL(login_attempts,0) < 5",
                Long.class);

        dto.setTotalUsers(totalUsers != null ? totalUsers : 0L);
        dto.setTotalTransactions(totalTransactions != null ? totalTransactions : 0L);
        dto.setTotalMoneyTransferred(totalMoney != null ? totalMoney : BigDecimal.ZERO);
        dto.setActiveUsers(activeUsers != null ? activeUsers : 0L);
        return dto;
    }

    public List<AdminLoanDto> findAllLoans() {
        String sql = "SELECT l.id, l.user_id, u.email, " +
                "       l.principal_amount AS amount, " +
                "       l.tenure_months   AS duration_months, " +
                "       l.interest_rate, l.status, l.created_at " +
                "FROM loans l JOIN users u ON l.user_id = u.id ORDER BY l.created_at DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<AdminLoanDto> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            AdminLoanDto dto = new AdminLoanDto();
            dto.setId(toLong(r.get("ID")));
            dto.setUserId(toLong(r.get("USER_ID")));
            dto.setUserEmail(toString(r.get("EMAIL")));
            dto.setAmount(toBigDecimal(r.get("AMOUNT")));
            dto.setDurationMonths(toInteger(r.get("DURATION_MONTHS")));
            dto.setInterestRate(toBigDecimal(r.get("INTEREST_RATE")));
            dto.setStatus(toString(r.get("STATUS")));
            dto.setCreatedAt(toTimestampAsDate(r.get("CREATED_AT")));
            out.add(dto);
        }
        return out;
    }

    public void updateLoanStatus(Long loanId, String status) {
        jdbcTemplate.update("UPDATE loans SET status = ? WHERE id = ?", status, loanId);
    }

    private static String toString(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private static Long toLong(Object o) {
        if (o == null)
            return null;
        if (o instanceof Number)
            return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer toInteger(Object o) {
        if (o == null)
            return null;
        if (o instanceof Number)
            return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null)
            return null;
        if (o instanceof BigDecimal)
            return (BigDecimal) o;
        if (o instanceof Number)
            return new BigDecimal(((Number) o).toString());
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.Date toTimestampAsDate(Object o) {
        if (o == null)
            return null;
        if (o instanceof java.util.Date)
            return (java.util.Date) o;
        if (o instanceof Timestamp)
            return new java.util.Date(((Timestamp) o).getTime());
        return null;
    }
}

