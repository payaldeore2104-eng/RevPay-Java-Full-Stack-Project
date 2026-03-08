package com.revpay.service;

import com.revpay.dto.UserRegistrationDto;
import com.revpay.repository.UserRepository;
import com.revpay.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

  ─────────────────────────────────────────────────────────────
    @Override
    public Long registerUser(UserRegistrationDto dto) throws Exception {
        String hashedPassword = SecurityUtil.hashPassword(dto.getPassword());

        // Determine role
        String role;
        if ("BUSINESS".equalsIgnoreCase(dto.getRoleName())) {
            role = "ROLE_BUSINESS";
        } else if ("ADMIN".equalsIgnoreCase(dto.getRoleName())) {
            role = "ROLE_ADMIN";
        } else {
            role = "ROLE_PERSONAL";
        }

        // Check for duplicate email
        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE email = ?", dto.getEmail());
            if (!existing.isEmpty()) {
                throw new Exception("This email is already registered. Please login.");
            }
        }

        // Check for duplicate phone
        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE phone = ?", dto.getPhone());
            if (!existing.isEmpty()) {
                throw new Exception("This phone number is already registered.");
            }
        }

        // Insert user using Oracle sequence
        jdbcTemplate.update(
                "INSERT INTO users (id, full_name, email, phone, password_hash, role, is_active, login_attempts, created_at, business_name, business_type, tax_id, address) "
                        +
                        "VALUES (user_seq.NEXTVAL, ?, ?, ?, ?, ?, 1, 0, SYSTIMESTAMP, ?, ?, ?, ?)",
                dto.getFullName(), dto.getEmail(), dto.getPhone(),
                hashedPassword, role, dto.getBusinessName(), dto.getBusinessType(), dto.getTaxId(), dto.getAddress());

        // Get the new user ID
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ? OR phone = ?",
                Long.class,
                dto.getEmail() != null ? dto.getEmail() : "",
                dto.getPhone() != null ? dto.getPhone() : "");

        if (userId == null) {
            throw new Exception("User created but ID could not be retrieved.");
        }

        // Auto-create wallet with ₹0
        jdbcTemplate.update(
                "INSERT INTO wallet (id, user_id, balance, currency, updated_at) " +
                        "VALUES (wallet_seq.NEXTVAL, ?, 0, 'INR', SYSTIMESTAMP)",
                userId);

        // Welcome notification
        try {
            jdbcTemplate.update(
                    "INSERT INTO notifications (id, user_id, message, is_read) " +
                            "VALUES (notification_seq.NEXTVAL, ?, ?, 0)",
                    userId, "Welcome to RevPay, " + dto.getFullName() + "! Your account is ready.");
        } catch (Exception ignored) {
            // Non-critical — ignore if notifications table doesn't exist yet
        }

        return userId;
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATE LOGIN — direct SQL, no stored procedure dependency
    // ─────────────────────────────────────────────────────────────
    @Override
    public boolean validateLogin(String loginId, String plainPassword) throws Exception {
        // Find user by email OR phone
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, password_hash, login_attempts, is_active FROM users " +
                        "WHERE email = ? OR phone = ?",
                loginId, loginId);

        if (rows.isEmpty()) {
            throw new Exception("No account found with that email or phone.");
        }

        Map<String, Object> user = rows.get(0);
        Long userId = ((Number) user.get("ID")).longValue();
        String hash = (String) user.get("PASSWORD_HASH");
        int attempts = ((Number) user.get("LOGIN_ATTEMPTS")).intValue();
        int isActive = ((Number) user.get("IS_ACTIVE")).intValue();

        // Check account lock
        if (isActive == 0 || attempts >= 5) {
            throw new Exception("Account is locked due to too many failed attempts. Contact support.");
        }

        // Verify password
        boolean valid = SecurityUtil.checkPassword(plainPassword, hash);
        if (valid) {
            // Reset failed attempts on success
            jdbcTemplate.update(
                    "UPDATE users SET login_attempts = 0, is_active = 1 WHERE id = ?", userId);
        } else {
            // Increment failed attempts
            jdbcTemplate.update(
                    "UPDATE users SET login_attempts = login_attempts + 1 WHERE id = ?", userId);
            // Lock if 5 attempts reached
            jdbcTemplate.update(
                    "UPDATE users SET is_active = 0 WHERE id = ? AND login_attempts >= 5", userId);
        }

        return valid;
    }

    // ─────────────────────────────────────────────────────────────
    // RESET PASSWORD
    // ─────────────────────────────────────────────────────────────
    @Override
    public void resetPassword(Long userId, String newPassword) throws Exception {
        String hashed = SecurityUtil.hashPassword(newPassword);
        jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE id = ?", hashed, userId);
    }

    // ─────────────────────────────────────────────────────────────
    // VALIDATE PIN
    // ─────────────────────────────────────────────────────────────
    @Override
    public boolean validatePin(Long userId, String pin) throws Exception {
        try {
            String hash = jdbcTemplate.queryForObject(
                    "SELECT transaction_pin FROM users WHERE id = ?", String.class, userId);
            if (hash == null)
                return false;
            return SecurityUtil.checkPassword(pin, hash);
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE PIN
    // ─────────────────────────────────────────────────────────────
    @Override
    public void updatePin(Long userId, String pin) throws Exception {
        String hashed = SecurityUtil.hashPassword(pin);
        jdbcTemplate.update("UPDATE users SET transaction_pin = ? WHERE id = ?", hashed, userId);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE PROFILE
    // ─────────────────────────────────────────────────────────────
    @Override
    public void updateProfile(Long userId, com.revpay.dto.ProfileUpdateDto dto) throws Exception {
        jdbcTemplate.update(
                "UPDATE users SET full_name = ?, phone = ?, email = ?, " +
                        "business_name = ?, business_type = ?, tax_id = ?, address = ? " +
                        "WHERE id = ?",
                dto.getFullName(), dto.getPhone(), dto.getEmail(),
                dto.getBusinessName(), dto.getBusinessType(), dto.getTaxId(), dto.getAddress(),
                userId);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE NOTIFICATION PREFERENCES
    // ─────────────────────────────────────────────────────────────
    @Override
    public void updateNotificationPreferences(Long userId, Integer prefTransactions, Integer prefRequests,
            Integer prefAlerts) throws Exception {
        jdbcTemplate.update(
                "UPDATE users SET pref_notif_transactions = ?, pref_notif_requests = ?, pref_notif_alerts = ? WHERE id = ?",
                prefTransactions, prefRequests, prefAlerts, userId);
    }

    // ─────────────────────────────────────────────────────────────
    // CHANGE PASSWORD (requires old password)
    // ─────────────────────────────────────────────────────────────
    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) throws Exception {
        // Verify old password
        String currentHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        if (currentHash == null || !SecurityUtil.checkPassword(oldPassword, currentHash)) {
            throw new Exception("Incorrect current password.");
        }
        // Update to new
        resetPassword(userId, newPassword);
    }

    // ─────────────────────────────────────────────────────────────
    // SETUP SECURITY QUESTIONS
    // ─────────────────────────────────────────────────────────────
    @Override
    public void setupSecurityQuestions(Long userId, java.util.List<com.revpay.dto.SecurityAnswerDto> answers)
            throws Exception {
        // Clear any existing answers first
        jdbcTemplate.update("DELETE FROM user_security_answers WHERE user_id = ?", userId);

        // Insert new
        String sql = "INSERT INTO user_security_answers (id, user_id, question_id, answer_hash) " +
                "VALUES (user_sec_answers_seq.NEXTVAL, ?, ?, ?)";
        for (com.revpay.dto.SecurityAnswerDto ans : answers) {
            String hashedAnswer = SecurityUtil.hashPassword(ans.getAnswer().trim().toLowerCase());
            jdbcTemplate.update(sql, userId, ans.getQuestionId(), hashedAnswer);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // VERIFY SECURITY ANSWERS (for password reset)
    // ─────────────────────────────────────────────────────────────
    @Override
    public boolean verifySecurityAnswers(String loginId, java.util.List<com.revpay.dto.SecurityAnswerDto> answers)
            throws Exception {
        // Resolve user ID
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email = ? OR phone = ?", loginId, loginId);
        if (rows.isEmpty())
            throw new Exception("No account found with that email/phone.");
        Long userId = ((Number) rows.get(0).get("ID")).longValue();

        // Check each answer
        for (com.revpay.dto.SecurityAnswerDto ans : answers) {
            List<String> hashes = jdbcTemplate.queryForList(
                    "SELECT answer_hash FROM user_security_answers WHERE user_id = ? AND question_id = ?",
                    String.class, userId, ans.getQuestionId());
            if (hashes.isEmpty())
                return false;

            String storedHash = hashes.get(0);
            if (!SecurityUtil.checkPassword(ans.getAnswer().trim().toLowerCase(), storedHash)) {
                return false;
            }
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // RESET PASSWORD WITH ANSWERS
    // ─────────────────────────────────────────────────────────────
    @Override
    public void resetPasswordWithAnswers(String loginId, String newPassword) throws Exception {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email = ? OR phone = ?", loginId, loginId);
        if (rows.isEmpty())
            throw new Exception("User not found.");
        Long userId = ((Number) rows.get(0).get("ID")).longValue();

        resetPassword(userId, newPassword);
    }

    // ─────────────────────────────────────────────────────────────
    // GET USER BY LOGIN ID — JDBC-based (avoids JPA schema issues)
    // ─────────────────────────────────────────────────────────────
    @Override
    public com.revpay.model.User getUserByLoginId(String loginId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM users WHERE email = ? OR phone = ?", loginId, loginId);
            if (rows.isEmpty())
                return null;

            Map<String, Object> row = rows.get(0);
            com.revpay.model.User u = new com.revpay.model.User();

            // Core fields always present
            u.setId(((Number) row.get("ID")).longValue());
            u.setFullName(safeStr(row, "FULL_NAME"));
            u.setEmail(safeStr(row, "EMAIL"));
            u.setPhone(safeStr(row, "PHONE"));
            u.setPasswordHash(safeStr(row, "PASSWORD_HASH"));
            u.setRole(safeStr(row, "ROLE"));

            if (row.get("IS_ACTIVE") != null)
                u.setIsLocked(((Number) row.get("IS_ACTIVE")).intValue());
            if (row.get("LOGIN_ATTEMPTS") != null)
                u.setFailedAttempts(((Number) row.get("LOGIN_ATTEMPTS")).intValue());

            // Optional extended fields (present only after schema update)
            u.setTransactionPin(safeStr(row, "TRANSACTION_PIN"));
            u.setBusinessName(safeStr(row, "BUSINESS_NAME"));
            u.setBusinessType(safeStr(row, "BUSINESS_TYPE"));
            u.setTaxId(safeStr(row, "TAX_ID"));
            u.setAddress(safeStr(row, "ADDRESS"));
            if (row.get("IS_VERIFIED") != null)
                u.setIsVerified(((Number) row.get("IS_VERIFIED")).intValue());

            return u;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String safeStr(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : null;
    }
}
