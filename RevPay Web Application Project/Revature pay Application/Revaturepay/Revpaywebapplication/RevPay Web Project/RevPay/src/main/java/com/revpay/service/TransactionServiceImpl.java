package com.revpay.service;

import com.revpay.model.MoneyRequest;
import com.revpay.model.User;
import com.revpay.model.Transaction;
import com.revpay.repository.MoneyRequestRepository;
import com.revpay.repository.TransactionRepository;
import com.revpay.util.CurrencyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.revpay.util.TransactionIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MoneyRequestRepository requestRepository;

    private User resolveUser(String identifier) throws Exception {
        try {
            Long searchId = null;
            try {
                searchId = Long.parseLong(identifier);
            } catch (NumberFormatException ignored) {
            }

            // Encrypt the identifier in case it's a bank account number
            String encryptedIdentifier = com.revpay.util.AESEncryptionUtil.encrypt(identifier);

            String sql = "SELECT DISTINCT u.id, u.email, u.phone, u.full_name, u.role " +
                    "FROM users u " +
                    "LEFT JOIN bank_accounts b ON u.id = b.user_id " +
                    "WHERE u.email = ? " +
                    "OR u.phone = ? " +
                    "OR u.id = ? " +
                    "OR LOWER(u.full_name) = LOWER(?) " +
                    "OR b.account_number_encrypted = ?";

            List<User> users = jdbcTemplate.query(sql, (rs, rowNum) -> {
                User u = new User();
                u.setId(rs.getLong("id"));
                u.setEmail(rs.getString("email"));
                u.setPhone(rs.getString("phone"));
                u.setFullName(rs.getString("full_name"));
                return u;
            }, identifier, identifier, searchId, identifier, encryptedIdentifier);

            return users.isEmpty() ? null : users.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional
    public Long transferMoney(Long senderId, String receiverEmailOrPhone, BigDecimal amount, String description)
            throws Exception {
        logger.info("Initiating money transfer. Sender ID: {}, Target: {}, Amount: {}", senderId, receiverEmailOrPhone,
                amount);
        User receiver = resolveUser(receiverEmailOrPhone);
        if (receiver == null) {
            logger.warn("Transfer failed. Target user {} not found.", receiverEmailOrPhone);
            throw new Exception("Receiver not found by email, phone, or account ID");
        }

        String transactionId = TransactionIdGenerator.generateTransactionId();

        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("transfer_money")
                .declareParameters(
                        new SqlParameter("p_sender_id", Types.NUMERIC),
                        new SqlParameter("p_receiver_id", Types.NUMERIC),
                        new SqlParameter("p_amount", Types.NUMERIC),
                        new SqlParameter("p_description", Types.VARCHAR),
                        new SqlParameter("p_transaction_id", Types.VARCHAR),
                        new SqlOutParameter("p_out_tx_id", Types.NUMERIC));

        MapSqlParameterSource in = new MapSqlParameterSource()
                .addValue("p_sender_id", senderId)
                .addValue("p_receiver_id", receiver.getId())
                .addValue("p_amount", amount)
                .addValue("p_description", description)
                .addValue("p_transaction_id", transactionId);

        Map<String, Object> out = jdbcCall.execute(in);
        Number txId = (Number) out.get("p_out_tx_id");
        // Notifications are inserted by the transfer_money stored procedure itself.
        // We patch reference_url so that clicking the notification navigates to
        // transaction history.
        try {
            User receiverUser = resolveUser(receiverEmailOrPhone);
            if (receiverUser != null) {
                // Update the most-recently created notification for the receiver (no URL yet)
                jdbcTemplate.update(
                        "UPDATE notifications SET reference_url = '/transactions/history' " +
                                "WHERE rowid = (" +
                                "  SELECT rowid FROM notifications " +
                                "  WHERE reference_url IS NULL AND user_id = ? " +
                                "  ORDER BY created_at DESC FETCH FIRST 1 ROWS ONLY)",
                        receiverUser.getId());
                // Update the most-recently created notification for the sender
                jdbcTemplate.update(
                        "UPDATE notifications SET reference_url = '/transactions/history' " +
                                "WHERE rowid = (" +
                                "  SELECT rowid FROM notifications " +
                                "  WHERE reference_url IS NULL AND user_id = ? " +
                                "  ORDER BY created_at DESC FETCH FIRST 1 ROWS ONLY)",
                        senderId);
            }
        } catch (Exception ignored) {
            // Notification patching failure should not block the transfer
        }

        if (txId != null) {
            logger.info("Money transfer completed successfully. Transaction ID: {}", txId);
        } else {
            logger.warn("Money transfer procedure returned null Transaction ID.");
        }

        return txId != null ? txId.longValue() : null;
    }

    @Override
    @Transactional
    public Long createRequest(Long requesterId, String requesteeEmailOrPhone, BigDecimal amount, String description)
            throws Exception {
        logger.info("Initiating money request. Requester ID: {}, Target: {}, Amount: {}", requesterId,
                requesteeEmailOrPhone, amount);
        User requestee = resolveUser(requesteeEmailOrPhone);
        if (requestee == null) {
            logger.warn("Money request failed. Target user {} not found.", requesteeEmailOrPhone);
            throw new Exception("Target user not found by email, phone, or account ID");
        }

        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("create_request")
                .declareParameters(
                        new SqlParameter("p_requester_id", Types.NUMERIC),
                        new SqlParameter("p_requestee_id", Types.NUMERIC),
                        new SqlParameter("p_amount", Types.NUMERIC),
                        new SqlParameter("p_description", Types.VARCHAR),
                        new SqlOutParameter("p_out_req_id", Types.NUMERIC));

        MapSqlParameterSource in = new MapSqlParameterSource()
                .addValue("p_requester_id", requesterId)
                .addValue("p_requestee_id", requestee.getId())
                .addValue("p_amount", amount)
                .addValue("p_description", description);

        Map<String, Object> out = jdbcCall.execute(in);
        Number reqId = (Number) out.get("p_out_req_id");
        // Notification is inserted by the create_request stored procedure itself.
        // We patch reference_url so that clicking the notification navigates to
        // transaction history.
        try {
            User requesteeUser = resolveUser(requesteeEmailOrPhone);
            if (requesteeUser != null) {
                // Update the most-recently created notification for the requestee (no URL yet)
                jdbcTemplate.update(
                        "UPDATE notifications SET reference_url = '/transactions/history' " +
                                "WHERE rowid = (" +
                                "  SELECT rowid FROM notifications " +
                                "  WHERE reference_url IS NULL AND user_id = ? " +
                                "  ORDER BY created_at DESC FETCH FIRST 1 ROWS ONLY)",
                        requesteeUser.getId());
            }
        } catch (Exception ignored) {
            // Notification patching failure should not block the request creation
        }

        if (reqId != null) {
            logger.info("Money request completed successfully. Request ID: {}", reqId);
        } else {
            logger.warn("Money request procedure returned null Request ID.");
        }

        return reqId != null ? reqId.longValue() : null;
    }

    @Override
    @Transactional
    public void updateRequestStatus(Long requestId, Long userId, String newStatus) throws Exception {
        if ("ACCEPTED".equals(newStatus)) {
            // 1. Fetch request details
            java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT requester_id, requestee_id, amount, status FROM money_requests WHERE id = ?",
                    requestId);
            if (rows.isEmpty())
                throw new Exception("Money request not found.");
            java.util.Map<String, Object> req = rows.get(0);

            String currentStatus = req.get("STATUS") != null ? req.get("STATUS").toString() : "";
            if (!"PENDING".equals(currentStatus))
                throw new Exception("Request is no longer pending.");

            Long requesterId = ((Number) req.get("REQUESTER_ID")).longValue();
            Long requesteeId = ((Number) req.get("REQUESTEE_ID")).longValue();
            java.math.BigDecimal amount = new java.math.BigDecimal(req.get("AMOUNT").toString());

            // 2. Transfer money: requestee (payer) → requester (recipient)
            String transactionId = TransactionIdGenerator.generateTransactionId();
            SimpleJdbcCall transferCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("transfer_money")
                    .declareParameters(
                            new SqlParameter("p_sender_id", Types.NUMERIC),
                            new SqlParameter("p_receiver_id", Types.NUMERIC),
                            new SqlParameter("p_amount", Types.NUMERIC),
                            new SqlParameter("p_description", Types.VARCHAR),
                            new SqlParameter("p_transaction_id", Types.VARCHAR),
                            new SqlOutParameter("p_out_tx_id", Types.NUMERIC));

            MapSqlParameterSource transferIn = new MapSqlParameterSource()
                    .addValue("p_sender_id", requesteeId) // payer
                    .addValue("p_receiver_id", requesterId) // recipient
                    .addValue("p_amount", amount)
                    .addValue("p_description", "Money Request Payment")
                    .addValue("p_transaction_id", transactionId);

            transferCall.execute(transferIn); // throws on insufficient balance

            // 3. Mark request as ACCEPTED using jdbcTemplate
            int updated = jdbcTemplate.update(
                    "UPDATE money_requests SET status = 'ACCEPTED' WHERE id = ?",
                    requestId);
            if (updated == 0) {
                throw new Exception("Could not update request status — request may have been already processed.");
            }

            // 4. Notify both parties
            try {
                notificationService.sendNotification(requesterId, "Money Request Accepted",
                        "✅ Your money request for " + CurrencyUtil.format(amount) + " was accepted and received.",
                        "REQUEST", "/transactions/history");
                notificationService.sendNotification(requesteeId, "Money Sent",
                        "💸 You sent " + CurrencyUtil.format(amount) + " to fulfil a money request.",
                        "TRANSACTION", "/transactions/history");
            } catch (Exception ignored) {
                // Notification failure should not block the acceptance
            }

            return; // done — do not fall through
        }

        // 1. Fetch request details first (for notifications)
        java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT requester_id, requestee_id, amount FROM money_requests WHERE id = ?", requestId);
        if (rows.isEmpty())
            throw new Exception("Money request not found.");

        java.util.Map<String, Object> req = rows.get(0);
        Long requesterId = ((Number) req.get("REQUESTER_ID")).longValue();
        Long requesteeId = ((Number) req.get("REQUESTEE_ID")).longValue();
        String amt = req.get("AMOUNT").toString();

        // 2. Update status
        int updated = jdbcTemplate.update(
                "UPDATE money_requests SET status = ? WHERE id = ?",
                newStatus, requestId);
        if (updated == 0)
            throw new Exception("Money request not found or already processed.");

        // 3. Insert notification with deep-link URL
        try {
            if ("DECLINED".equals(newStatus)) {
                notificationService.sendNotification(requesterId, "Request Declined",
                        "Your request for " + CurrencyUtil.format(Double.parseDouble(amt)) + " was declined.",
                        "REQUEST", "/transactions/history");
            } else if ("CANCELLED".equals(newStatus)) {
                notificationService.sendNotification(requesteeId, "Request Cancelled",
                        "The request for " + CurrencyUtil.format(Double.parseDouble(amt)) + " was cancelled.",
                        "REQUEST", "/transactions/history");
            }
        } catch (Exception ignored) {
            // Notification failure should not block the status update
        }
    }

    @Override
    public List<Transaction> getUserTransactions(Long userId) {
        return transactionRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
    }

    @Override
    public List<MoneyRequest> getUserRequests(Long userId) {
        return requestRepository.findByRequesterIdOrRequesteeIdOrderByCreatedAtDesc(userId, userId);
    }

    // ── JDBC Methods bypassing JPA ───────────────────────────────────────────

    // ── JDBC Methods bypassing JPA ───────────────────────────────────────────

    @Override
    public List<Map<String, Object>> getUserTransactionsJdbc(Long userId, String type, String status, String startDate,
            String endDate, BigDecimal minAmount, BigDecimal maxAmount, String searchQuery) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.id, t.transaction_id, t.transaction_ref_id, t.amount, t.description, t.status, t.created_at, t.transaction_type, "
                        +
                        "  CASE WHEN t.sender_id = ? THEN 'SENT' ELSE 'RECEIVED' END as direction, " +
                        "  CASE WHEN t.sender_id = ? THEN r.full_name ELSE s.full_name END as counterparty_name, " +
                        "  CASE WHEN t.sender_id = ? " +
                        "       THEN COALESCE(r.email, r.phone) " +
                        "       ELSE COALESCE(s.email, s.phone) END as counterparty_contact, " +
                        "  SUM(CASE WHEN t.receiver_id = ? THEN t.amount ELSE -t.amount END) " +
                        "      OVER (ORDER BY t.created_at ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) as running_balance "
                        +
                        "FROM transactions t " +
                        "LEFT JOIN users s ON t.sender_id = s.id " +
                        "LEFT JOIN users r ON t.receiver_id = r.id " +
                        "WHERE (t.sender_id = ? OR t.receiver_id = ?) ");

        List<Object> params = new ArrayList<>();
        params.add(userId); // direction CASE
        params.add(userId); // counterparty_name CASE
        params.add(userId); // counterparty_contact CASE
        params.add(userId); // running_balance SUM
        params.add(userId); // WHERE sender_id
        params.add(userId); // WHERE receiver_id

        if (type != null && !type.isEmpty()) {
            if ("SENT".equalsIgnoreCase(type)) {
                sql.append(" AND t.sender_id = ? AND t.transaction_type = 'TRANSFER'");
                params.add(userId);
            } else if ("RECEIVED".equalsIgnoreCase(type)) {
                sql.append(" AND t.receiver_id = ? AND t.transaction_type = 'TRANSFER'");
                params.add(userId);
            } else {
                sql.append(" AND t.transaction_type = ?");
                params.add(type.toUpperCase());
            }
        }

        if (status != null && !status.isEmpty()) {
            sql.append(" AND t.status = ?");
            params.add(status.toUpperCase());
        }

        if (startDate != null && !startDate.isEmpty()) {
            sql.append(" AND t.created_at >= TO_TIMESTAMP(?, 'YYYY-MM-DD')");
            params.add(startDate);
        }

        if (endDate != null && !endDate.isEmpty()) {
            sql.append(" AND t.created_at <= TO_TIMESTAMP(? || ' 23:59:59', 'YYYY-MM-DD HH24:MI:SS')");
            params.add(endDate);
        }

        if (minAmount != null) {
            sql.append(" AND t.amount >= ?");
            params.add(minAmount);
        }

        if (maxAmount != null) {
            sql.append(" AND t.amount <= ?");
            params.add(maxAmount);
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String likeParam = "%" + searchQuery.toLowerCase() + "%";
            sql.append(" AND (LOWER(t.description) LIKE ? OR ")
                    .append("LOWER(t.transaction_id) LIKE ? OR ")
                    .append("LOWER(CASE WHEN t.sender_id = ? THEN r.full_name ELSE s.full_name END) LIKE ?)");
            params.add(likeParam);
            params.add(likeParam);
            params.add(userId);
            params.add(likeParam);
        }

        sql.append(" ORDER BY t.created_at DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @Override
    public List<Map<String, Object>> getUserRequestsJdbc(Long userId) {
        String sql = "SELECT m.id, m.amount, m.description, m.status, m.created_at, " +
                "  CASE WHEN m.requester_id = ? THEN 'REQUESTED_BY_ME' ELSE 'REQUESTED_FROM_ME' END as direction, " +
                "  CASE WHEN m.requester_id = ? THEN re.full_name ELSE rr.full_name END as counterparty_name, " +
                "  m.requester_id, m.requestee_id " +
                "FROM money_requests m " +
                "LEFT JOIN users rr ON m.requester_id = rr.id " +
                "LEFT JOIN users re ON m.requestee_id = re.id " +
                "WHERE m.requester_id = ? OR m.requestee_id = ? " +
                "ORDER BY m.created_at DESC";
        return jdbcTemplate.queryForList(sql, userId, userId, userId, userId);
    }
}
