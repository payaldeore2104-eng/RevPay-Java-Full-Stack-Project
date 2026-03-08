package com.revpay.controller;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.revpay.model.User;
import com.revpay.service.TransactionService;
import com.revpay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.revpay.service.ReceiptService;
import com.revpay.util.CurrencyUtil;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    @Autowired
    private TransactionService txService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReceiptService receiptService;

    private String getLoggedInLoginId(HttpSession session) {
        return (String) session.getAttribute("loggedInUser");
    }

    // Uses JDBC-based lookup — avoids JPA schema mismatch
    private User getLoggedInUser(HttpSession session) {
        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return null;
        return userService.getUserByLoginId(loginId);
    }

    private Long resolveUserId(String loginId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = ? OR phone = ?",
                    Long.class, loginId, loginId);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/send")
    public String showSendMoneyForm(HttpSession session, Model model) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        String loginId = getLoggedInLoginId(session);
        Long userId = resolveUserId(loginId);
        if (userId != null) {
            try {
                java.util.List<java.util.Map<String, Object>> defaultCards = jdbcTemplate.queryForList(
                        "SELECT id, card_holder_name, card_number_encrypted, expiry_date FROM cards WHERE user_id = ? AND is_default = 1",
                        userId);
                if (!defaultCards.isEmpty()) {
                    java.util.Map<String, Object> dc = defaultCards.get(0);
                    String encNum = (String) dc.get("CARD_NUMBER_ENCRYPTED");
                    try {
                        String full = com.revpay.util.AESEncryptionUtil.decrypt(encNum);
                        dc.put("CARD_NUMBER_MASKED",
                                "**** **** **** " + full.substring(Math.max(0, full.length() - 4)));
                    } catch (Exception ignored) {
                        dc.put("CARD_NUMBER_MASKED", "**** **** **** ????");
                    }
                    model.addAttribute("defaultCard", dc);
                }
            } catch (Exception ignored) {
            }
        }
        return "send-money";
    }

    @PostMapping("/send")
    public String sendMoney(@RequestParam("receiver") String receiver,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("description") String description,
            @RequestParam("pin") String pin,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", "Session expired. Please log in again.");
            return "redirect:/transactions/send";
        }

        // ── PIN verification ────────────────────────────────
        try {
            boolean pinValid = userService.validatePin(userId, pin);
            if (!pinValid) {
                redirectAttributes.addFlashAttribute("error", "Incorrect Transaction PIN. Please try again.");
                return "redirect:/transactions/send";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "PIN verification failed: " + e.getMessage());
            return "redirect:/transactions/send";
        }

        // ── Proceed with transfer ───────────────────────────
        try {
            txService.transferMoney(userId, receiver, amount, description);
            return "redirect:/transactions/history?success=sent";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/transactions/send";
        }
    }

    @PostMapping("/send-via-card")
    public String sendViaCard(@RequestParam("receiver") String receiver,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("description") String description,
            @RequestParam("cardPin") String cardPin,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null) {
            redirectAttributes.addFlashAttribute("error", "Session expired. Please log in again.");
            return "redirect:/transactions/send";
        }

        // Verify card PIN against the user's default card
        try {
            String storedPin = jdbcTemplate.queryForObject(
                    "SELECT card_pin FROM cards WHERE user_id = ? AND is_default = 1",
                    String.class, userId);
            if (storedPin == null || storedPin.isEmpty()) {
                redirectAttributes.addFlashAttribute("error",
                        "No card PIN set for your default card. Please add a PIN to your card.");
                return "redirect:/transactions/send";
            }
            if (!cardPin.equals(storedPin)) {
                redirectAttributes.addFlashAttribute("error", "Incorrect Card PIN. Please try again.");
                return "redirect:/transactions/send";
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            redirectAttributes.addFlashAttribute("error",
                    "No default card found. Please set a default card from Wallet > Manage Cards.");
            return "redirect:/transactions/send";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Card PIN verification failed: " + e.getMessage());
            return "redirect:/transactions/send";
        }

        // Proceed with wallet transfer (card authorizes but wallet funds the payment)
        try {
            txService.transferMoney(userId, receiver, amount, description + " [via Card]");
            return "redirect:/transactions/history?success=sent";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/transactions/send";
        }
    }

    @GetMapping("/request")
    public String showRequestMoneyForm(HttpSession session) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        return "request-money";
    }

    @PostMapping("/request")
    public String requestMoney(@RequestParam("requestee") String requestee,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("description") String description,
            HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null) {
            redirectAttributes.addFlashAttribute("error", "Session error. Please log in again.");
            return "redirect:/transactions/request";
        }
        try {
            txService.createRequest(user.getId(), requestee, amount, description);
            return "redirect:/transactions/history?success=requested";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/transactions/request";
        }
    }

    @GetMapping("/history")
    public String history(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "minAmount", required = false) BigDecimal minAmount,
            @RequestParam(value = "maxAmount", required = false) BigDecimal maxAmount,
            @RequestParam(value = "searchQuery", required = false) String searchQuery,
            @RequestParam(value = "success", required = false) String success,
            @RequestParam(value = "error", required = false) String error,
            HttpSession session, Model model) {

        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null) {
            model.addAttribute("transactions", java.util.Collections.emptyList());
            model.addAttribute("requests", java.util.Collections.emptyList());
            return "transaction-history";
        }

        // Pass success / error flash messages to the view
        if (success != null)
            model.addAttribute("successMsg", resolveSuccessMsg(success));
        if (error != null)
            model.addAttribute("errorMsg", error.replace("+", " "));

        try {
            model.addAttribute("transactions", txService.getUserTransactionsJdbc(
                    user.getId(), type, status, startDate, endDate, minAmount, maxAmount, searchQuery));
            model.addAttribute("requests", txService.getUserRequestsJdbc(user.getId()));

            // Retain filter state in UI
            model.addAttribute("type", type);
            model.addAttribute("status", status);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("minAmount", minAmount);
            model.addAttribute("maxAmount", maxAmount);
            model.addAttribute("searchQuery", searchQuery);

        } catch (Exception e) {
            model.addAttribute("transactions", java.util.Collections.emptyList());
            model.addAttribute("requests", java.util.Collections.emptyList());
            model.addAttribute("errorMsg", "Could not load transactions: " + e.getMessage());
        }
        model.addAttribute("currentUser", user);
        return "transaction-history";
    }

    private String resolveSuccessMsg(String code) {
        switch (code) {
            case "sent":
                return "Payment sent successfully!";
            case "requested":
                return "Money request sent successfully!";
            case "updated":
                return "Request updated successfully!";
            default:
                return "Operation completed successfully.";
        }
    }

    @PostMapping("/request/update")
    public String updateRequestStatus(@RequestParam("requestId") Long requestId,
            @RequestParam("status") String status,
            @RequestParam(value = "pin", required = false) String pin,
            HttpSession session) {
        if (getLoggedInLoginId(session) == null)
            return "redirect:/login";
        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null)
            return "redirect:/login";

        // PIN required only when accepting (money is being sent)
        if ("ACCEPTED".equals(status)) {
            if (pin == null || pin.trim().isEmpty()) {
                return "redirect:/transactions/history?error=PIN+is+required+to+accept+payment";
            }
            try {
                boolean pinValid = userService.validatePin(user.getId(), pin);
                if (!pinValid) {
                    return "redirect:/transactions/history?error=Incorrect+Transaction+PIN.+Please+try+again.";
                }
            } catch (Exception e) {
                return "redirect:/transactions/history?error=PIN+verification+failed";
            }
        }

        try {
            txService.updateRequestStatus(requestId, user.getId(), status);
            return "redirect:/transactions/history?success=updated";
        } catch (Exception e) {
            e.printStackTrace();
            // Use a safe, URL-encoded error message — raw e.getMessage() can contain
            // characters like & / ? that break redirect URLs and cause 500 errors
            String safeMsg;
            if (e.getMessage() != null && e.getMessage().contains("Insufficient")) {
                safeMsg = "Insufficient+wallet+balance+to+complete+this+payment";
            } else {
                try {
                    safeMsg = java.net.URLEncoder.encode("Payment failed: " + e.getMessage(), "UTF-8");
                } catch (Exception ex) {
                    safeMsg = "Payment+failed.+Please+try+again.";
                }
            }
            return "redirect:/transactions/history?error=" + safeMsg;
        }
    }

    @GetMapping("/export")
    public void exportCSV(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "minAmount", required = false) BigDecimal minAmount,
            @RequestParam(value = "maxAmount", required = false) BigDecimal maxAmount,
            @RequestParam(value = "searchQuery", required = false) String searchQuery,
            HttpSession session, HttpServletResponse response) throws Exception {

        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null)
            return;

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"transactions.csv\"");
        PrintWriter writer = response.getWriter();
        writer.println("Date,Type,Direction,Contact,Amount,Status,Balance,Description");

        txService
                .getUserTransactionsJdbc(user.getId(), type, status, startDate, endDate, minAmount, maxAmount,
                        searchQuery)
                .forEach(tx -> {
                    writer.println(
                            tx.get("CREATED_AT") + "," +
                                    tx.get("TRANSACTION_TYPE") + "," +
                                    tx.get("DIRECTION") + ",\"" +
                                    (tx.get("COUNTERPARTY_CONTACT") == null ? "" : tx.get("COUNTERPARTY_CONTACT"))
                                    + "\"," +
                                    "INR " + tx.get("AMOUNT") + "," +
                                    tx.get("STATUS") + "," +
                                    "INR " + tx.get("RUNNING_BALANCE")
                                    + ",\"" +
                                    (tx.get("DESCRIPTION") == null ? "" : tx.get("DESCRIPTION")) + "\"");
                });
        writer.flush();
    }

    @GetMapping("/export/pdf")
    public void exportPDF(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "minAmount", required = false) BigDecimal minAmount,
            @RequestParam(value = "maxAmount", required = false) BigDecimal maxAmount,
            @RequestParam(value = "searchQuery", required = false) String searchQuery,
            HttpSession session, HttpServletResponse response) throws Exception {

        User user = getLoggedInUser(session);
        if (user == null || user.getId() == null)
            return;

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"transactions.pdf\"");

        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();
        document.add(new Paragraph("RevPay Transaction History\n\n"));

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        float[] columnWidths = { 2.0f, 1.5f, 1.5f, 2.0f, 1.2f, 1.5f, 1.5f, 2.5f };
        table.setWidths(columnWidths);

        // Header row styling
        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9,
                com.lowagie.text.Font.BOLD);
        String[] headers = { "Date", "Type", "Direction", "Contact", "Amount", "Status", "Balance", "Description" };
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new java.awt.Color(41, 128, 185));
            cell.setPadding(5);
            table.addCell(cell);
        }

        // Data rows
        com.lowagie.text.Font dataFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8);
        txService
                .getUserTransactionsJdbc(user.getId(), type, status, startDate, endDate, minAmount, maxAmount,
                        searchQuery)
                .forEach(tx -> {
                    table.addCell(new Phrase(String.valueOf(tx.get("CREATED_AT")), dataFont));
                    table.addCell(new Phrase(String.valueOf(tx.get("TRANSACTION_TYPE")), dataFont));
                    table.addCell(new Phrase(String.valueOf(tx.get("DIRECTION")), dataFont));
                    table.addCell(new Phrase(
                            tx.get("COUNTERPARTY_CONTACT") == null ? "-"
                                    : String.valueOf(tx.get("COUNTERPARTY_CONTACT")),
                            dataFont));
                    table.addCell(new Phrase(
                            CurrencyUtil.format(((Number) tx.get("AMOUNT")).doubleValue()).replace("\u20B9", "INR "),
                            dataFont));
                    table.addCell(new Phrase(String.valueOf(tx.get("STATUS")), dataFont));
                    table.addCell(new Phrase(
                            CurrencyUtil.format(((Number) tx.get("RUNNING_BALANCE")).doubleValue()).replace("\u20B9",
                                    "INR "),
                            dataFont));
                    table.addCell(new Phrase(
                            tx.get("DESCRIPTION") == null ? "-" : String.valueOf(tx.get("DESCRIPTION")), dataFont));
                });

        document.add(table);
        document.close();
    }

    @PostMapping("/pay-invoice")
    public String payInvoice(@RequestParam("invoiceId") Long invoiceId,
            @RequestParam("businessId") Long businessId,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("description") String description,
            @RequestParam("pin") String pin,
            HttpSession session, Model model) {

        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return "redirect:/login";

        Long userId = resolveUserId(loginId);
        if (userId == null) {
            return "redirect:/dashboard?error=Session expired";
        }

        // PIN verification
        try {
            boolean pinValid = userService.validatePin(userId, pin);
            if (!pinValid) {
                return "redirect:/dashboard?error=Incorrect Transaction PIN. Please try again.";
            }
        } catch (Exception e) {
            return "redirect:/dashboard?error=PIN verification failed: " + e.getMessage();
        }

        // Proceed with transfer and update invoice
        try {
            txService.transferMoney(userId, businessId.toString(), amount, "Invoice Payment: " + description);

            // Update invoice status to PAID
            org.springframework.jdbc.core.simple.SimpleJdbcCall jdbcCall = new org.springframework.jdbc.core.simple.SimpleJdbcCall(
                    jdbcTemplate)
                    .withProcedureName("update_invoice_status")
                    .declareParameters(
                            new org.springframework.jdbc.core.SqlParameter("p_invoice_id", java.sql.Types.NUMERIC),
                            new org.springframework.jdbc.core.SqlParameter("p_new_status", java.sql.Types.VARCHAR));
            jdbcCall.execute(new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                    .addValue("p_invoice_id", invoiceId)
                    .addValue("p_new_status", "PAID"));

            return "redirect:/dashboard?success=invoice_paid";
        } catch (Exception e) {
            return "redirect:/dashboard?error=" + e.getMessage();
        }
    }

    // ── GET /transactions/{id}/receipt ────────────────────────────────────────
    @GetMapping("/{id}/receipt")
    public ResponseEntity<InputStreamResource> downloadReceipt(
            @PathVariable("id") Long txId,
            HttpSession session) {

        String loginId = getLoggedInLoginId(session);
        if (loginId == null)
            return ResponseEntity.status(302).header("Location", "/login").build();

        Long userId = resolveUserId(loginId);
        if (userId == null)
            return ResponseEntity.status(403).build();

        try {
            // Fetch the transaction with sender/receiver names
            java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT t.id, t.transaction_id, t.transaction_ref_id, t.amount, t.status, t.created_at, " +
                            "       t.description, t.transaction_type, " +
                            "       s.full_name AS sender_name, r.full_name AS receiver_name, " +
                            "       t.sender_id, t.receiver_id " +
                            "FROM transactions t " +
                            "LEFT JOIN users s ON t.sender_id  = s.id " +
                            "LEFT JOIN users r ON t.receiver_id = r.id " +
                            "WHERE t.id = ?",
                    txId);

            if (rows.isEmpty())
                return ResponseEntity.notFound().build();

            java.util.Map<String, Object> tx = rows.get(0);

            // Security: only sender or receiver may download
            Long senderId = tx.get("SENDER_ID") != null ? ((Number) tx.get("SENDER_ID")).longValue() : null;
            Long receiverId = tx.get("RECEIVER_ID") != null ? ((Number) tx.get("RECEIVER_ID")).longValue() : null;
            if (!userId.equals(senderId) && !userId.equals(receiverId))
                return ResponseEntity.status(403).build();

            // Generate PDF
            ByteArrayInputStream pdf = receiptService.generateReceipt(tx);

            String refId = tx.get("TRANSACTION_REF_ID") != null
                    ? tx.get("TRANSACTION_REF_ID").toString()
                    : "receipt";

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition",
                    "inline; filename=revpay-receipt-" + refId + ".pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new InputStreamResource(pdf));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
