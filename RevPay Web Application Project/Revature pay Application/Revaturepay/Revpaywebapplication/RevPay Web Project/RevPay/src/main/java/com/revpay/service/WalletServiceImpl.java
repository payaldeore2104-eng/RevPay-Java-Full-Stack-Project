package com.revpay.service;

import com.revpay.model.BankAccount;
import com.revpay.model.Card;
import com.revpay.model.User;
import com.revpay.model.Wallet;
import com.revpay.repository.BankAccountRepository;
import com.revpay.repository.CardRepository;
import com.revpay.repository.UserRepository;
import com.revpay.repository.WalletRepository;
import com.revpay.util.AESEncryptionUtil;
import com.revpay.util.CurrencyUtil;
import com.revpay.util.SecurityUtil;
import com.revpay.util.TransactionIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;
import java.util.Map;

@Service
public class WalletServiceImpl implements WalletService {

    private static final Logger logger = LoggerFactory.getLogger(WalletServiceImpl.class);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @Override
    public Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId).orElse(null);
    }

    @Override
    @Transactional
    public BigDecimal addMoney(Long userId, BigDecimal amount, String sourceType, Long sourceId, String cardPin)
            throws Exception {
        // 1. Validate Source
        if ("CARD".equalsIgnoreCase(sourceType)) {
            Card card = cardRepository.findById(sourceId)
                    .orElseThrow(() -> new Exception("Selected card not found."));

            if (card.getUser() == null || !card.getUser().getId().equals(userId)) {
                throw new Exception("Unauthorized card access.");
            }

            if (card.getCardPin() == null || !card.getCardPin().equals(cardPin)) {
                throw new Exception("Incorrect Card PIN. Please try again.");
            }
        } else if ("BANK".equalsIgnoreCase(sourceType)) {
            BankAccount bankAccount = bankAccountRepository.findById(sourceId)
                    .orElseThrow(() -> new Exception("Selected bank account not found."));

            if (bankAccount.getUser() == null || !bankAccount.getUser().getId().equals(userId)) {
                throw new Exception("Unauthorized bank account access.");
            }

            User user = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));
            if (user.getTransactionPin() == null || !SecurityUtil.checkPassword(cardPin, user.getTransactionPin())) {
                throw new Exception("Incorrect Transaction PIN. Please try again.");
            }
        } else {
            throw new Exception("Invalid funding source selected.");
        }

        // 2. Add Money to Wallet
        String transactionId = TransactionIdGenerator.generateTransactionId();
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("add_money")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.NUMERIC),
                        new SqlParameter("p_amount", Types.NUMERIC),
                        new SqlParameter("p_transaction_id", Types.VARCHAR),
                        new SqlOutParameter("p_out_new_balance", Types.NUMERIC));
        MapSqlParameterSource in = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_amount", amount)
                .addValue("p_transaction_id", transactionId);

        Map<String, Object> out = jdbcCall.execute(in);
        Number newBalance = (Number) out.get("p_out_new_balance");

        // 3. Notify user of successful deposit
        try {
            String sourceName = "CARD".equalsIgnoreCase(sourceType) ? "card" : "bank account";
            notificationService.sendNotification(userId, "Money Added",
                    CurrencyUtil.format(amount) + " was added to your wallet from your " + sourceName + ".",
                    "TRANSACTION", "/wallet");
        } catch (Exception ignored) {
        }

        logger.info("Money added successfully for User {}. Added: {}, New Balance: {}", userId, amount, newBalance);
        return new BigDecimal(newBalance.toString());
    }

    @Override
    @Transactional
    public BigDecimal withdrawMoney(Long userId, BigDecimal amount, String destinationType, Long destinationId,
            String cardPin)
            throws Exception {
        // 1. Validate Destination
        if ("CARD".equalsIgnoreCase(destinationType)) {
            Card card = cardRepository.findById(destinationId)
                    .orElseThrow(() -> new Exception("Selected card not found."));

            if (card.getUser() == null || !card.getUser().getId().equals(userId)) {
                throw new Exception("Unauthorized card access.");
            }

            if (card.getCardPin() == null || !card.getCardPin().equals(cardPin)) {
                throw new Exception("Incorrect Card PIN. Please try again.");
            }
        } else if ("BANK".equalsIgnoreCase(destinationType)) {
            BankAccount bankAccount = bankAccountRepository.findById(destinationId)
                    .orElseThrow(() -> new Exception("Selected bank account not found."));

            if (bankAccount.getUser() == null || !bankAccount.getUser().getId().equals(userId)) {
                throw new Exception("Unauthorized bank account access.");
            }

            User user = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));
            if (user.getTransactionPin() == null || !SecurityUtil.checkPassword(cardPin, user.getTransactionPin())) {
                throw new Exception("Incorrect Transaction PIN. Please try again.");
            }
        } else {
            throw new Exception("Invalid withdrawal destination selected.");
        }

        String transactionId = TransactionIdGenerator.generateTransactionId();
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName("withdraw_money")
                .declareParameters(
                        new SqlParameter("p_user_id", Types.NUMERIC),
                        new SqlParameter("p_amount", Types.NUMERIC),
                        new SqlParameter("p_transaction_id", Types.VARCHAR),
                        new SqlOutParameter("p_out_new_balance", Types.NUMERIC));
        MapSqlParameterSource in = new MapSqlParameterSource()
                .addValue("p_user_id", userId)
                .addValue("p_amount", amount)
                .addValue("p_transaction_id", transactionId);

        Map<String, Object> out = jdbcCall.execute(in);
        Number newBalance = (Number) out.get("p_out_new_balance");
        BigDecimal balance = new BigDecimal(newBalance.toString());

        // Fire low-balance alert in Java (avoids Oracle compile-time dependency on
        // notifications table)
        if (balance.compareTo(new BigDecimal("50")) < 0) {
            try {
                logger.warn("User {} balance dropped below ₹50. Alerting user.", userId);
                notificationService.sendNotification(userId, "Low Balance Alert",
                        "Your wallet balance has dropped below \u20B950. Current balance: "
                                + CurrencyUtil.format(balance),
                        "ALERT");
            } catch (Exception ignored) {
                // Non-critical – don't fail the withdrawal if notification fails
                logger.error("Failed to send low balance notification to user {}.", userId, ignored);
            }
        }

        // Notify user of successful withdrawal
        try {
            String destName = "CARD".equalsIgnoreCase(destinationType) ? "card" : "bank account";
            notificationService.sendNotification(userId, "Money Withdrawn",
                    CurrencyUtil.format(amount) + " was withdrawn from your wallet to your " + destName + ".",
                    "TRANSACTION", "/wallet");
        } catch (Exception ignored) {
        }

        logger.info("Money withdrawn successfully by User {}. Withdrawn: {}, Remaining Balance: {}", userId, amount,
                balance);
        return balance;
    }

    @Override
    public List<Card> getCardsByUserId(Long userId) {
        return cardRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public void addCard(Long userId, String cardNumber, String cardHolder, String expiryDate, String cvv,
            String billingAddress, String cardPin, boolean isDefault, String cardType)
            throws Exception {
        User user = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));

        Card card = new Card();
        card.setUser(user);
        card.setCardHolderName(cardHolder);
        card.setExpiryDate(expiryDate);
        card.setCvv(cvv);
        card.setBillingAddress(billingAddress);
        card.setCardPin(cardPin != null ? cardPin : "");
        card.setCardType(cardType);
        card.setCardNumberEncrypted(AESEncryptionUtil.encrypt(cardNumber));

        if (isDefault) {
            jdbcTemplate.update("UPDATE cards SET is_default = 0 WHERE user_id = ?", userId);
            card.setIsDefault(1);
        } else {
            card.setIsDefault(0);
        }

        cardRepository.save(card);
        notificationService.sendNotification(userId, "Card Added", "A new card ending in "
                + cardNumber.substring(cardNumber.length() - 4) + " was successfully linked to your wallet.", "ALERT");
    }

    @Override
    @Transactional
    public void editCard(Long cardId, String cardHolder, String expiryDate) throws Exception {
        Card card = cardRepository.findById(cardId).orElseThrow(() -> new Exception("Card not found"));
        card.setCardHolderName(cardHolder);
        card.setExpiryDate(expiryDate);
        cardRepository.save(card);
        notificationService.sendNotification(card.getUser().getId(), "Card Updated",
                "The details for your card have been updated.", "ALERT");
    }

    @Override
    @Transactional
    public void removeCard(Long cardId) throws Exception {
        Card card = cardRepository.findById(cardId).orElseThrow(() -> new Exception("Card not found"));
        Long userId = card.getUser().getId();
        cardRepository.deleteById(cardId);
        notificationService.sendNotification(userId, "Card Removed", "A card was removed from your wallet.", "ALERT");
    }

    @Override
    @Transactional
    public void setDefaultCard(Long userId, Long cardId) throws Exception {
        jdbcTemplate.update("UPDATE cards SET is_default = 0 WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE cards SET is_default = 1 WHERE id = ? AND user_id = ?", cardId, userId);
    }

    @Override
    public List<BankAccount> getBankAccountsByUserId(Long userId) {
        return bankAccountRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public void addBankAccount(Long userId, String accountNumber, String routingNumber, String bankName,
            String accountType)
            throws Exception {
        User user = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));

        BankAccount account = new BankAccount();
        account.setUser(user);
        account.setBankName(bankName);
        account.setRoutingNumber(routingNumber);
        account.setAccountType(accountType != null && !accountType.isEmpty() ? accountType.toUpperCase() : "SAVINGS");
        account.setAccountNumberEncrypted(AESEncryptionUtil.encrypt(accountNumber));

        bankAccountRepository.save(account);
        notificationService.sendNotification(userId, "Bank Account Linked",
                "A " + bankName + " business account was successfully linked.", "ALERT");
    }

    @Override
    @Transactional
    public void removeBankAccount(Long accountId) throws Exception {
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new Exception("Bank Account not found"));
        Long userId = account.getUser().getId();
        String bankName = account.getBankName();
        bankAccountRepository.deleteById(accountId);
        notificationService.sendNotification(userId, "Bank Account Unlinked",
                "Your " + bankName + " business account has been removed.", "ALERT");
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getWalletTransactions(Long userId, int limit) {
        try {
            // Fetch all transactions where user is sender or receiver, most recent first
            String sql = "SELECT id, amount, transaction_type, description, status, created_at, " +
                    "       CASE WHEN sender_id = ? THEN 'DEBIT' ELSE 'CREDIT' END AS direction " +
                    "FROM transactions " +
                    "WHERE sender_id = ? OR receiver_id = ? " +
                    "ORDER BY created_at DESC " +
                    "FETCH FIRST ? ROWS ONLY";
            return jdbcTemplate.queryForList(sql, userId, userId, userId, limit);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }
}
