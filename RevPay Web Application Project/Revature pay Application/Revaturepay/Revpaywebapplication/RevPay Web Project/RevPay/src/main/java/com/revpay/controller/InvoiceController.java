package com.revpay.controller;

import com.revpay.service.TransactionService;
import com.revpay.service.UserService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.revpay.util.CurrencyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Controller
@RequestMapping("/invoices")
public class InvoiceController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionService txService;

    @Autowired
    private UserService userService;

    // ── helpers ─────────────────────────────────────────────────────────────

    private String getLoggedInEmail(HttpSession session) {
        return (String) session.getAttribute("loggedInUser");
    }

    private Long getUserId(HttpSession session) {
        String email = getLoggedInEmail(session);
        if (email == null)
            return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = ? OR phone = ?",
                    Long.class, email, email);
        } catch (Exception e) {
            return null;
        }
    }

    // ── GET /invoices/{id} ───────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String viewInvoice(@PathVariable("id") Long invoiceId,
            @RequestParam(value = "error", required = false) String error,
            HttpSession session, Model model) {

        if (getLoggedInEmail(session) == null)
            return "redirect:/login";

        Long userId = getUserId(session);
        if (userId == null)
            return "redirect:/login";

        try {
            // Resolve user's email and phone so we can match any customerIdentifier format
            String userEmail = "";
            String userPhone = "";
            try {
                userEmail = jdbcTemplate.queryForObject(
                        "SELECT email FROM users WHERE id = ?", String.class, userId);
                userPhone = jdbcTemplate.queryForObject(
                        "SELECT NVL(phone, '') FROM users WHERE id = ?", String.class, userId);
            } catch (Exception ignored) {
            }

            // Allow business owner OR customer (matched by email, phone, or account ID)
            Map<String, Object> invoice = jdbcTemplate.queryForMap(
                    "SELECT i.*, u.full_name AS business_name, u.email AS business_email, " +
                            "       u.id AS business_user_id " +
                            "FROM invoice i JOIN users u ON i.business_id = u.id " +
                            "WHERE i.id = ? " +
                            "  AND (i.business_id = ? " +
                            "    OR LOWER(i.customer_email) = LOWER(?)" +
                            "    OR LOWER(i.customer_email) = LOWER(?)" +
                            "    OR i.customer_email = ?)",
                    invoiceId, userId, userEmail, userPhone, String.valueOf(userId));

            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT * FROM invoice_items WHERE invoice_id = ? ORDER BY id", invoiceId);

            Number businessUserId = (Number) invoice.get("BUSINESS_USER_ID");
            boolean isCustomer = (businessUserId == null ||
                    businessUserId.longValue() != userId.longValue());

            model.addAttribute("invoice", invoice);
            model.addAttribute("items", items);
            model.addAttribute("isCustomer", isCustomer);

            if (error != null) {
                model.addAttribute("error", error.replace("+", " "));
            }
        } catch (Exception e) {
            return "redirect:/dashboard?error=Invoice+not+found+or+you+do+not+have+access";
        }
        return "customer-invoice";
    }

    // ── POST /invoices/{id}/pay ──────────────────────────────────────────────

    @PostMapping("/{id}/pay")
    public void payInvoice(@PathVariable("id") Long invoiceId,
            @RequestParam("pin") String pin,
            HttpSession session,
            HttpServletResponse response) throws Exception {

        if (getLoggedInEmail(session) == null) {
            response.sendRedirect("/login");
            return;
        }

        Long userId = getUserId(session);
        if (userId == null) {
            response.sendRedirect("/login");
            return;
        }

        // ── PIN verification ─────────────────────────────────────────────────
        try {
            if (!userService.validatePin(userId, pin)) {
                response.sendRedirect("/invoices/" + invoiceId +
                        "?error=Incorrect+Transaction+PIN.+Please+try+again.");
                return;
            }
        } catch (Exception e) {
            response.sendRedirect("/invoices/" + invoiceId + "?error=PIN+verification+failed");
            return;
        }

        // ── Fetch invoice ────────────────────────────────────────────────────
        Map<String, Object> invoice;
        try {
            invoice = jdbcTemplate.queryForMap(
                    "SELECT i.*, u.full_name AS business_name, u.id AS bid " +
                            "FROM invoice i JOIN users u ON i.business_id = u.id WHERE i.id = ?",
                    invoiceId);
        } catch (Exception e) {
            response.sendRedirect("/dashboard?error=Invoice+not+found");
            return;
        }

        String currentStatus = String.valueOf(invoice.get("STATUS"));
        if ("PAID".equals(currentStatus)) {
            response.sendRedirect("/invoices/" + invoiceId + "?error=Invoice+is+already+paid");
            return;
        }
        if ("CANCELLED".equals(currentStatus)) {
            response.sendRedirect("/invoices/" + invoiceId + "?error=This+invoice+has+been+cancelled");
            return;
        }

        BigDecimal amount = new BigDecimal(invoice.get("TOTAL_AMOUNT").toString());
        Long businessId = ((Number) invoice.get("BID")).longValue();
        String businessName = String.valueOf(invoice.get("BUSINESS_NAME"));

        // ── Transfer money ───────────────────────────────────────────────────
        String txnRef = "N/A";
        try {
            Long txId = txService.transferMoney(userId, businessId.toString(), amount,
                    "Invoice Payment #" + invoiceId + " – " + businessName);
            if (txId != null) {
                try {
                    txnRef = jdbcTemplate.queryForObject(
                            "SELECT NVL(transaction_id, 'TXN-' || TO_CHAR(id)) " +
                                    "FROM transactions WHERE id = ?",
                            String.class, txId);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            String msg = (e.getMessage() != null) ? e.getMessage() : "Payment failed";
            response.sendRedirect("/invoices/" + invoiceId + "?error=" +
                    java.net.URLEncoder.encode(msg, "UTF-8"));
            return;
        }

        // ── Mark invoice PAID ────────────────────────────────────────────────
        try {
            SimpleJdbcCall call = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("update_invoice_status")
                    .declareParameters(
                            new SqlParameter("p_invoice_id", Types.NUMERIC),
                            new SqlParameter("p_new_status", Types.VARCHAR));
            call.execute(new MapSqlParameterSource()
                    .addValue("p_invoice_id", invoiceId)
                    .addValue("p_new_status", "PAID"));
        } catch (Exception ignored) {
            /* don't block PDF on status update failure */ }

        // ── Customer name for receipt ─────────────────────────────────────────
        String customerName = "Customer";
        try {
            customerName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?", String.class, userId);
        } catch (Exception ignored) {
        }

        // ── Stream PDF receipt ───────────────────────────────────────────────
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"PaymentReceipt-Invoice" + invoiceId + ".pdf\"");

        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, response.getOutputStream());
        doc.open();

        com.lowagie.text.Font fBig = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 26,
                com.lowagie.text.Font.BOLD);
        com.lowagie.text.Font fHead = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 13,
                com.lowagie.text.Font.BOLD);
        com.lowagie.text.Font fBody = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 11);
        com.lowagie.text.Font fSmall = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9,
                com.lowagie.text.Font.ITALIC);

        // Logo / App name
        Paragraph brand = new Paragraph("RevPay", fBig);
        brand.setAlignment(Paragraph.ALIGN_CENTER);
        doc.add(brand);

        Paragraph receiptTitle = new Paragraph("Payment Receipt", fHead);
        receiptTitle.setAlignment(Paragraph.ALIGN_CENTER);
        doc.add(receiptTitle);
        doc.add(new Paragraph(" "));

        // Horizontal rule
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell ruleCell = new PdfPCell(new Phrase(""));
        ruleCell.setBorderWidthBottom(1.5f);
        ruleCell.setBorderWidthTop(0);
        ruleCell.setBorderWidthLeft(0);
        ruleCell.setBorderWidthRight(0);
        ruleCell.setPaddingBottom(4);
        rule.addCell(ruleCell);
        doc.add(rule);
        doc.add(new Paragraph(" "));

        // Status stamp
        Paragraph stamp = new Paragraph("✓  PAYMENT CONFIRMED", fHead);
        stamp.setAlignment(Paragraph.ALIGN_CENTER);
        doc.add(stamp);
        doc.add(new Paragraph(" "));

        // Details table
        PdfPTable detail = new PdfPTable(2);
        detail.setWidthPercentage(75);
        detail.setHorizontalAlignment(Element.ALIGN_CENTER);
        detail.setWidths(new float[] { 2f, 3f });

        String[][] rows = {
                { "Invoice #", "#" + invoiceId },
                { "Transaction Ref", txnRef },
                { "Payment Date", new SimpleDateFormat("dd MMM yyyy  HH:mm:ss").format(new Date()) },
                { "Paid To", businessName },
                { "Paid By", customerName },
                { "Amount Paid", CurrencyUtil.format(amount) },
                { "Status", "PAID" }
        };

        for (String[] row : rows) {
            boolean isAmount = "Amount Paid".equals(row[0]);
            PdfPCell lCell = new PdfPCell(new Phrase(row[0], fHead));
            lCell.setBorder(PdfPCell.NO_BORDER);
            lCell.setPadding(7);
            detail.addCell(lCell);

            PdfPCell vCell = new PdfPCell(new Phrase(row[1], isAmount ? fHead : fBody));
            vCell.setBorder(PdfPCell.NO_BORDER);
            vCell.setPadding(7);
            detail.addCell(vCell);
        }
        doc.add(detail);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(" "));

        // Footer
        Paragraph footer = new Paragraph(
                "This is an auto-generated receipt from RevPay. Please keep it for your records.", fSmall);
        footer.setAlignment(Paragraph.ALIGN_CENTER);
        doc.add(footer);

        doc.close();
    }
}
