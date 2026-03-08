package com.revpay.controller;

import com.revpay.model.BankAccount;
import com.revpay.model.Card;
import com.revpay.model.Wallet;
import com.revpay.service.WalletService;
import com.revpay.util.AESEncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/wallet")
public class WalletController {

    // Low balance threshold — alert shown when balance drops below this
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("50.00");

    @Autowired
    private WalletService walletService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── JDBC-based user resolution (no JPA schema issues) ─────────────────
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

    // ── Helper: get wallet and auto-create if missing ─────────────────────
    private Wallet getOrCreateWallet(Long userId) {
        try {
            Wallet wallet = walletService.getWalletByUserId(userId);
            if (wallet == null) {
                // Auto-create wallet with ₹0
                jdbcTemplate.update(
                        "INSERT INTO wallet (id, user_id, balance, currency, updated_at) " +
                                "VALUES (wallet_seq.NEXTVAL, ?, 0, 'INR', SYSTIMESTAMP)",
                        userId);
                wallet = walletService.getWalletByUserId(userId);
            }
            return wallet;
        } catch (Exception e) {
            Wallet stub = new Wallet();
            stub.setBalance(BigDecimal.ZERO);
            stub.setCurrency("INR");
            stub.setUpdatedAt(new Date());
            return stub;
        }
    }

    // ── GET /wallet ────────────────────────────────────────────────────────
    @GetMapping
    public String showWallet(HttpSession session, Model model) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";

        Wallet wallet = getOrCreateWallet(userId);
        model.addAttribute("wallet", wallet);

        try {
            String fullName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?",
                    String.class, userId);
            model.addAttribute("user", fullName != null ? fullName : loginId);
        } catch (Exception e) {
            model.addAttribute("user", loginId);
        }

        // ── Low balance alert ──────────────────────────────────────────────
        if (wallet.getBalance() != null
                && wallet.getBalance().compareTo(LOW_BALANCE_THRESHOLD) < 0) {
            model.addAttribute("lowBalance", true);
            model.addAttribute("threshold", LOW_BALANCE_THRESHOLD);
        }

        // ── Recent wallet transaction history (last 10) ────────────────────
        List<Map<String, Object>> history = walletService.getWalletTransactions(userId, 10);
        model.addAttribute("history", history);

        // ── Cards and Banks for Dropdowns ──────────────────────────────
        List<Card> cards = walletService.getCardsByUserId(userId);
        model.addAttribute("cards", cards);
        model.addAttribute("bankAccounts", walletService.getBankAccountsByUserId(userId));

        return "wallet";
    }

    // ── POST /wallet/add-money ─────────────────────────────────────────────
    @PostMapping("/add-money")
    public String addMoney(@RequestParam("amount") BigDecimal amount,
            @RequestParam("fundingSource") String fundingSource,
            @RequestParam(value = "cardPin", required = false) String cardPin,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";

        try {
            String[] parts = fundingSource.split("_");
            if (parts.length != 2)
                throw new Exception("Invalid funding source format.");
            String sourceType = parts[0];
            Long sourceId = Long.parseLong(parts[1]);

            walletService.addMoney(userId, amount, sourceType, sourceId, cardPin);
            redirectAttributes.addFlashAttribute("successMsg", "Money added to wallet successfully!");
            return "redirect:/wallet";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not add money: " + e.getMessage());
            return "redirect:/wallet";
        }
    }

    // ── POST /wallet/withdraw-money ────────────────────────────────────────
    @PostMapping("/withdraw-money")
    public String withdrawMoney(@RequestParam("amount") BigDecimal amount,
            @RequestParam("destination") String destination,
            @RequestParam(value = "cardPin", required = false) String cardPin,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";

        try {
            String[] parts = destination.split("_");
            if (parts.length != 2)
                throw new Exception("Invalid withdrawal destination format.");
            String destType = parts[0];
            Long destId = Long.parseLong(parts[1]);

            walletService.withdrawMoney(userId, amount, destType, destId, cardPin);
            redirectAttributes.addFlashAttribute("successMsg", "Withdrawal processed successfully!");
            return "redirect:/wallet";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not withdraw: " + e.getMessage());
            return "redirect:/wallet";
        }
    }

    // ── GET /wallet/cards ─────────────────────────────────────────────────
    @GetMapping("/cards")
    public String manageCards(HttpSession session, Model model) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";

        List<Card> cards = new ArrayList<>();
        try {
            cards = walletService.getCardsByUserId(userId);
            for (Card c : cards) {
                try {
                    String fullNum = AESEncryptionUtil.decrypt(c.getCardNumberEncrypted());
                    c.setCardNumberEncrypted("**** **** **** " +
                            fullNum.substring(Math.max(0, fullNum.length() - 4)));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            model.addAttribute("error", "Could not load cards: " + e.getMessage());
        }

        List<BankAccount> bankAccounts = new ArrayList<>();
        try {
            bankAccounts = walletService.getBankAccountsByUserId(userId);
            for (BankAccount ba : bankAccounts) {
                try {
                    String fullNum = AESEncryptionUtil.decrypt(ba.getAccountNumberEncrypted());
                    ba.setAccountNumberEncrypted("**** " +
                            fullNum.substring(Math.max(0, fullNum.length() - 4)));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            model.addAttribute("error", "Could not load bank accounts: " + e.getMessage());
        }

        try {
            String roleSql = "SELECT role FROM users WHERE id = ?";
            String role = jdbcTemplate.queryForObject(roleSql, String.class, userId);
            model.addAttribute("userRole", role);
        } catch (Exception ignored) {
        }

        model.addAttribute("cards", cards);
        model.addAttribute("bankAccounts", bankAccounts);

        try {
            String fullName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?",
                    String.class, userId);
            model.addAttribute("user", fullName != null ? fullName : loginId);
        } catch (Exception e) {
            model.addAttribute("user", loginId);
        }

        return "manage-cards";
    }

    // ── GET /wallet/cards/add ─────────────────────────────────────────────
    @GetMapping("/cards/add")
    public String showAddCardForm(HttpSession session) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        return "add-card";
    }

    // ── POST /wallet/cards/add ────────────────────────────────────────────
    @PostMapping("/cards/add")
    public String addCard(@RequestParam("cardNumber") String cardNumber,
            @RequestParam("cardHolder") String cardHolder,
            @RequestParam("expiryDate") String expiryDate,
            @RequestParam("cvv") String cvv,
            @RequestParam("billingAddress") String billingAddress,
            @RequestParam(value = "cardPin", required = false) String cardPin,
            @RequestParam(value = "isDefault", required = false) boolean isDefault,
            @RequestParam("cardType") String cardType,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";
        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";
        try {
            walletService.addCard(userId, cardNumber, cardHolder, expiryDate, cvv, billingAddress, cardPin, isDefault,
                    cardType);
            return "redirect:/wallet/cards?success=added";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not add card: " + e.getMessage());
            return "redirect:/wallet/cards/add";
        }
    }

    // ── POST /wallet/cards/delete ─────────────────────────────────────────
    @PostMapping("/cards/delete")
    public String deleteCard(@RequestParam("cardId") Long cardId, HttpSession session) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        try {
            walletService.removeCard(cardId);
            return "redirect:/wallet/cards?success=deleted";
        } catch (Exception e) {
            return "redirect:/wallet/cards?error=could_not_delete";
        }
    }

    // ── POST /wallet/cards/default ────────────────────────────────────────
    @PostMapping("/cards/default")
    public String setDefaultCard(@RequestParam("cardId") Long cardId, HttpSession session) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";
        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";
        try {
            walletService.setDefaultCard(userId, cardId);
            return "redirect:/wallet/cards?success=default_set";
        } catch (Exception e) {
            return "redirect:/wallet/cards?error=could_not_set";
        }
    }

    // ── POST /wallet/cards/edit ───────────────────────────────────────────
    @PostMapping("/cards/edit")
    public String editCard(@RequestParam("cardId") Long cardId,
            @RequestParam("cardHolder") String cardHolder,
            @RequestParam("expiryDate") String expiryDate,
            HttpSession session) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        try {
            walletService.editCard(cardId, cardHolder, expiryDate);
            return "redirect:/wallet/cards?success=card_edited";
        } catch (Exception e) {
            return "redirect:/wallet/cards?error=could_not_edit";
        }
    }

    // ── POST /wallet/bank-accounts/add ────────────────────────────────────
    @PostMapping("/bank-accounts/add")
    public String addBankAccount(@RequestParam("bankName") String bankName,
            @RequestParam("accountNumber") String accountNumber,
            @RequestParam("ifscCode") String ifscCode,
            @RequestParam(value = "accountType", defaultValue = "SAVINGS") String accountType,
            HttpSession session, Model model) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return "redirect:/login";

        // Validate IFSC pattern (11 alphanumeric characters)
        String formattedIfsc = ifscCode != null ? ifscCode.trim().toUpperCase() : "";
        if (!formattedIfsc.matches("^[A-Z0-9]{11}$")) {
            return "redirect:/wallet/cards?error="
                    + java.net.URLEncoder.encode(
                            "Invalid IFSC Code format. Must be exactly 11 alphanumeric characters.",
                            java.nio.charset.StandardCharsets.UTF_8);
        }

        try {
            walletService.addBankAccount(userId, accountNumber, formattedIfsc, bankName, accountType);
            return "redirect:/wallet/cards?success=bank_added";
        } catch (Exception e) {
            return "redirect:/wallet/cards?error="
                    + java.net.URLEncoder.encode(e.getMessage() != null ? e.getMessage() : "Could not link account",
                            java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // ── POST /wallet/bank-accounts/delete ─────────────────────────────────
    @PostMapping("/bank-accounts/delete")
    public String deleteBankAccount(@RequestParam("accountId") Long accountId, HttpSession session) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        try {
            walletService.removeBankAccount(accountId);
            return "redirect:/wallet/cards?success=bank_deleted";
        } catch (Exception e) {
            return "redirect:/wallet/cards?error=could_not_delete_bank";
        }
    }
}
