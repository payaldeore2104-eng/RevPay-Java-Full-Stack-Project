package com.revpay.controller;

import com.revpay.service.NotificationService;
import com.revpay.util.CurrencyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Controller
@RequestMapping("/business")
public class BusinessController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

    @Value("${app.upload.dir:uploads/verification-docs}")
    private String uploadDir;

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

    // Returns the role stored in the DB for the currently logged-in user
    private String getUserRole(HttpSession session) {
        String email = getLoggedInEmail(session);
        if (email == null)
            return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT role FROM users WHERE email = ? OR phone = ?",
                    String.class, email, email);
        } catch (Exception e) {
            return null;
        }
    }

    // Returns true only for ROLE_BUSINESS and ROLE_ADMIN
    private boolean isBusinessUser(HttpSession session) {
        String role = getUserRole(session);
        return "ROLE_BUSINESS".equals(role) || "ROLE_ADMIN".equals(role);
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";
        Long userId = getUserId(session);

        List<Map<String, Object>> invoices = jdbcTemplate
                .queryForList("SELECT * FROM invoice WHERE business_id = ? ORDER BY created_at DESC", userId);
        model.addAttribute("invoices", invoices);

        Double totalRev = 0.0;
        try {
            totalRev = jdbcTemplate.queryForObject(
                    "SELECT SUM(total_amount) FROM invoice WHERE business_id = ? AND status = 'PAID'", Double.class,
                    userId);
            if (totalRev == null)
                totalRev = 0.0;
        } catch (Exception e) {
        }
        model.addAttribute("totalRevenue", totalRev);

        Double pendingAmount = 0.0;
        try {
            pendingAmount = jdbcTemplate.queryForObject(
                    "SELECT SUM(total_amount) FROM invoice WHERE business_id = ? AND status IN ('DRAFT', 'SENT', 'OVERDUE')",
                    Double.class, userId);
            if (pendingAmount == null)
                pendingAmount = 0.0;
        } catch (Exception e) {
        }
        model.addAttribute("pendingAmount", pendingAmount);

        // Pass user's name for the header
        try {
            String fullName = jdbcTemplate.queryForObject(
                    "SELECT full_name FROM users WHERE id = ?",
                    String.class, userId);
            model.addAttribute("user", fullName != null ? fullName : getLoggedInEmail(session));
        } catch (Exception e) {
            model.addAttribute("user", getLoggedInEmail(session));
        }

        return "business-dashboard";
    }

    @GetMapping("/invoices/export/pdf")
    public void exportBusinessInvoicesPDF(HttpSession session, HttpServletResponse response) throws Exception {
        if (!isBusinessUser(session)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
            return;
        }

        Long userId = getUserId(session);
        List<Map<String, Object>> invoices = jdbcTemplate.queryForList(
                "SELECT * FROM invoice WHERE business_id = ? ORDER BY created_at DESC", userId);

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"Business_Invoices_Report.pdf\"");

        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 16,
                com.lowagie.text.Font.BOLD);
        Paragraph title = new Paragraph("Business Invoices Report", titleFont);
        title.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(title);

        // Calculate totals
        double totalRev = 0;
        double totalOut = 0;
        for (Map<String, Object> inv : invoices) {
            String status = String.valueOf(inv.get("STATUS"));
            double amt = 0;
            if (inv.get("TOTAL_AMOUNT") != null) {
                // Number handles both BigDecimal / Double safely
                amt = ((Number) inv.get("TOTAL_AMOUNT")).doubleValue();
            }
            if ("PAID".equals(status))
                totalRev += amt;
            else if ("SENT".equals(status) || "OVERDUE".equals(status))
                totalOut += amt;
        }

        document.add(new Paragraph(" "));
        document.add(new Paragraph("Generated On: " + new Date().toString()));
        document.add(new Paragraph("Total Revenue (Paid): " + CurrencyUtil.format(totalRev)));
        document.add(new Paragraph("Outstanding (Sent/Overdue): " + CurrencyUtil.format(totalOut)));
        document.add(new Paragraph("Total Invoices: " + invoices.size()));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 1f, 2.5f, 1.5f, 1.5f, 1.2f, 1.5f });

        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10,
                com.lowagie.text.Font.BOLD);
        String[] headers = { "ID", "Customer", "Date", "Due Date", "Status", "Amount" };
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new java.awt.Color(23, 162, 184)); // Matches dashboard theme
            cell.setPadding(6);
            table.addCell(cell);
        }

        com.lowagie.text.Font dataFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        for (Map<String, Object> inv : invoices) {
            table.addCell(new Phrase("INV-" + inv.get("ID"), dataFont));
            table.addCell(new Phrase(String.valueOf(inv.get("CUSTOMER_EMAIL")), dataFont));

            String cDate = inv.get("CREATED_AT") != null ? sdf.format((Date) inv.get("CREATED_AT")) : "-";
            table.addCell(new Phrase(cDate, dataFont));

            String dDate = inv.get("DUE_DATE") != null ? sdf.format((Date) inv.get("DUE_DATE")) : "-";
            table.addCell(new Phrase(dDate, dataFont));

            table.addCell(new Phrase(String.valueOf(inv.get("STATUS")), dataFont));

            String amtStr = inv.get("TOTAL_AMOUNT") != null
                    ? CurrencyUtil.format(((Number) inv.get("TOTAL_AMOUNT")).doubleValue())
                    : CurrencyUtil.format(0.0);
            table.addCell(new Phrase(amtStr, dataFont));
        }

        document.add(table);
        document.close();
    }

    @GetMapping("/invoices/create")
    public String showCreateInvoice(HttpSession session) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";
        return "invoice-create";
    }

    @PostMapping("/invoices/create")
    public String createInvoice(@RequestParam("customerIdentifier") String customerIdentifier,
            @RequestParam("customerName") String customerName,
            @RequestParam("customerAddress") String customerAddress,
            @RequestParam("paymentTerms") String paymentTerms,
            @RequestParam("dueDate") String dueDateStr,
            @RequestParam("description") String description,
            @RequestParam("quantity") Integer quantity,
            @RequestParam("price") Double price,
            @RequestParam(value = "tax", defaultValue = "0") Double tax,
            HttpSession session) {
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";
        try {
            Long userId = getUserId(session);
            java.util.Date dueDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(dueDateStr);

            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("create_invoice")
                    .declareParameters(
                            new SqlParameter("p_business_id", Types.NUMERIC),
                            new SqlParameter("p_customer_email", Types.VARCHAR),
                            new SqlParameter("p_customer_name", Types.VARCHAR),
                            new SqlParameter("p_customer_address", Types.VARCHAR),
                            new SqlParameter("p_payment_terms", Types.VARCHAR),
                            new SqlParameter("p_due_date", Types.DATE),
                            new SqlOutParameter("p_out_invoice_id", Types.NUMERIC));
            MapSqlParameterSource in = new MapSqlParameterSource()
                    .addValue("p_business_id", userId)
                    .addValue("p_customer_email", customerIdentifier)
                    .addValue("p_customer_name", customerName)
                    .addValue("p_customer_address", customerAddress)
                    .addValue("p_payment_terms", paymentTerms)
                    .addValue("p_due_date", dueDate);
            java.util.Map<String, Object> out = jdbcCall.execute(in);
            Number invId = (Number) out.get("p_out_invoice_id");

            if (invId != null) {
                double total = (quantity * price) + tax;
                jdbcTemplate.update(
                        "INSERT INTO invoice_items (invoice_id, description, quantity, unit_price, tax, line_total) VALUES (?, ?, ?, ?, ?, ?)",
                        invId.longValue(), description, quantity, price, tax, total);
                jdbcTemplate.update("UPDATE invoice SET total_amount = ? WHERE id = ?", total, invId.longValue());

                // Send notification to the customer
                try {
                    Long searchId = null;
                    try {
                        searchId = Long.parseLong(customerIdentifier);
                    } catch (NumberFormatException ignored) {
                    }

                    Long customerId = jdbcTemplate.queryForObject(
                            "SELECT id FROM users WHERE email = ? OR phone = ? OR id = ?", Long.class,
                            customerIdentifier, customerIdentifier, searchId);
                    if (customerId != null) {
                        String businessName = jdbcTemplate.queryForObject(
                                "SELECT full_name FROM users WHERE id = ?", String.class, userId);
                        notificationService.sendNotification(customerId, "New Invoice",
                                "You have received a new invoice from " + businessName + " for "
                                        + CurrencyUtil.format(total) + ". Due on: " + dueDateStr,
                                "ALERT",
                                "/invoices/" + invId.longValue());
                    }
                } catch (Exception ignored) {
                    // Customer might not be registered or not found. Don't block invoice creation.
                }
            }
            return "redirect:/business/dashboard?success=invoice_created";
        } catch (Exception e) {
            return "redirect:/business/invoices/create?error=" + e.getMessage();
        }
    }

    @GetMapping("/invoices/{id}")
    public String viewInvoice(@PathVariable("id") Long invoiceId, HttpSession session, Model model) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";

        Long userId = getUserId(session);
        try {
            // Allow both the business owner and the customer to view the invoice
            Map<String, Object> invoice = jdbcTemplate.queryForMap(
                    "SELECT i.*, u.full_name as business_name, u.email as business_email " +
                            "FROM invoice i JOIN users u ON i.business_id = u.id " +
                            "WHERE i.id = ? AND (i.business_id = ? OR i.customer_email = " +
                            "  (SELECT email FROM users WHERE id = ?))",
                    invoiceId, userId, userId);

            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                    "SELECT * FROM invoice_items WHERE invoice_id = ?", invoiceId);

            model.addAttribute("invoice", invoice);
            model.addAttribute("items", items);
        } catch (Exception e) {
            return "redirect:/dashboard?error=Invoice+not+found";
        }
        return "invoice-detail";
    }

    @GetMapping("/invoices/{id}/pdf")
    public void exportInvoicePDF(@PathVariable("id") Long invoiceId, HttpSession session, HttpServletResponse response)
            throws Exception {
        if (getLoggedInEmail(session) == null)
            return;

        Long userId = getUserId(session);
        Map<String, Object> invoice;
        List<Map<String, Object>> items;

        try {
            // Exact same security block as viewInvoice
            invoice = jdbcTemplate.queryForMap(
                    "SELECT i.*, u.full_name as business_name, u.email as business_email " +
                            "FROM invoice i JOIN users u ON i.business_id = u.id " +
                            "WHERE i.id = ? AND (i.business_id = ? OR i.customer_email = " +
                            "  (SELECT email FROM users WHERE id = ?))",
                    invoiceId, userId, userId);

            items = jdbcTemplate.queryForList(
                    "SELECT * FROM invoice_items WHERE invoice_id = ?", invoiceId);
        } catch (Exception e) {
            // Unauthorized or not found
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Invoice not found or access denied.");
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"Invoice-" + invoiceId + ".pdf\"");

        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // Header
        com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18,
                com.lowagie.text.Font.BOLD);
        Paragraph title = new Paragraph("INVOICE #" + invoiceId, titleFont);
        title.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(title);

        document.add(new Paragraph(" "));
        document.add(new Paragraph(
                "Description: " + (invoice.get("DESCRIPTION") != null ? invoice.get("DESCRIPTION") : "N/A")));
        document.add(new Paragraph("Status: " + invoice.get("STATUS")));
        document.add(new Paragraph("Created Date: " + invoice.get("CREATED_AT")));
        document.add(new Paragraph("Due Date: " + invoice.get("DUE_DATE")));
        document.add(new Paragraph(
                "Payment Terms: " + (invoice.get("PAYMENT_TERMS") != null ? invoice.get("PAYMENT_TERMS") : "N/A")));

        document.add(new Paragraph(" "));

        // Business and Customer Info
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[] { 1f, 1f });

        PdfPCell businessCell = new PdfPCell(
                new Paragraph("From:\n" + invoice.get("business_name") + "\n" + invoice.get("business_email")));
        businessCell.setBorder(PdfPCell.NO_BORDER);
        infoTable.addCell(businessCell);

        PdfPCell customerCell = new PdfPCell(new Paragraph(
                "To:\n" + (invoice.get("CUSTOMER_NAME") != null ? invoice.get("CUSTOMER_NAME") : "Unknown") + "\n"
                        + invoice.get("CUSTOMER_EMAIL") + "\n"
                        + (invoice.get("CUSTOMER_ADDRESS") != null ? invoice.get("CUSTOMER_ADDRESS") : "")));
        customerCell.setBorder(PdfPCell.NO_BORDER);
        infoTable.addCell(customerCell);

        document.add(infoTable);

        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        // Items Table
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 3f, 1f, 1.5f, 1f, 1.5f });

        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10,
                com.lowagie.text.Font.BOLD);
        String[] headers = { "Description", "Qty", "Unit Price", "Tax", "Total" };
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new java.awt.Color(41, 128, 185));
            cell.setPadding(6);
            table.addCell(cell);
        }

        com.lowagie.text.Font dataFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9);

        for (Map<String, Object> item : items) {
            table.addCell(new Phrase(String.valueOf(item.get("DESCRIPTION")), dataFont));
            table.addCell(new Phrase(String.valueOf(item.get("QUANTITY")), dataFont));
            table.addCell(
                    new Phrase(CurrencyUtil.format(((Number) item.get("UNIT_PRICE")).doubleValue()), dataFont));
            table.addCell(new Phrase(CurrencyUtil.format(((Number) item.get("TAX")).doubleValue()), dataFont));
            table.addCell(
                    new Phrase(CurrencyUtil.format(((Number) item.get("LINE_TOTAL")).doubleValue()), dataFont));
        }

        document.add(table);

        document.add(new Paragraph(" "));

        Paragraph grandTotal = new Paragraph(
                "Grand Total: " + CurrencyUtil.format(((Number) invoice.get("TOTAL_AMOUNT")).doubleValue()),
                titleFont);
        grandTotal.setAlignment(Paragraph.ALIGN_RIGHT);
        document.add(grandTotal);

        document.close();
    }

    @PostMapping("/invoices/cancel")
    public String cancelInvoice(HttpSession session, @RequestParam("invoiceId") Long invoiceId) {
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";

        jdbcTemplate.update("UPDATE invoice SET status = 'CANCELLED' WHERE id = ? AND business_id = ?",
                invoiceId, getUserId(session));
        return "redirect:/business/dashboard?success=invoice_cancelled";
    }

    @GetMapping("/analytics")
    public String analytics(HttpSession session, Model model) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";

        Long userId = getUserId(session);

        // 1. Total Revenue (Paid Invoices)
        Double totalRev = 0.0;
        try {
            totalRev = jdbcTemplate.queryForObject(
                    "SELECT SUM(total_amount) FROM invoice WHERE business_id = ? AND status = 'PAID'",
                    Double.class, userId);
            if (totalRev == null)
                totalRev = 0.0;
        } catch (Exception e) {
        }
        model.addAttribute("totalRevenue", totalRev);

        // 2. Outstanding Invoices (Draft + Sent + Overdue)
        Double outstandingRev = 0.0;
        try {
            outstandingRev = jdbcTemplate.queryForObject(
                    "SELECT SUM(total_amount) FROM invoice WHERE business_id = ? AND status IN ('DRAFT', 'SENT', 'OVERDUE')",
                    Double.class, userId);
            if (outstandingRev == null)
                outstandingRev = 0.0;
        } catch (Exception e) {
        }
        model.addAttribute("outstandingRevenue", outstandingRev);

        // 3. Transactions Summaries (Count of Paid vs Unpaid)
        Integer totalInvoices = 0;
        Integer paidInvoices = 0;
        try {
            totalInvoices = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice WHERE business_id = ?",
                    Integer.class, userId);
            paidInvoices = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM invoice WHERE business_id = ? AND status = 'PAID'", Integer.class, userId);
        } catch (Exception e) {
        }
        model.addAttribute("totalInvoices", totalInvoices != null ? totalInvoices : 0);
        model.addAttribute("paidInvoices", paidInvoices != null ? paidInvoices : 0);

        // 4. Revenue Reports (Daily, Weekly, Monthly)

        // Daily: revenue per day for the last 30 days
        List<Map<String, Object>> dailyRevenue = new java.util.ArrayList<>();
        try {
            dailyRevenue = jdbcTemplate.queryForList(
                    "SELECT TO_CHAR(TRUNC(created_at), 'YYYY-MM-DD') AS day_key, " +
                            "       TO_CHAR(TRUNC(created_at), 'DD Mon') AS day_label, " +
                            "       SUM(total_amount) AS revenue " +
                            "FROM invoice WHERE business_id = ? AND status = 'PAID' " +
                            "  AND created_at >= TRUNC(SYSDATE) - 29 " +
                            "GROUP BY TRUNC(created_at) " +
                            "ORDER BY TRUNC(created_at) ASC",
                    userId);
        } catch (Exception e) {
            /* ignore */ }
        model.addAttribute("dailyRevenue", dailyRevenue);

        List<Map<String, Object>> weeklyRevenue = new java.util.ArrayList<>();
        try {
            weeklyRevenue = jdbcTemplate.queryForList(
                    "WITH weeks AS ( " +
                            "    SELECT TRUNC(SYSDATE, 'IW') - (7 * (LEVEL - 1)) AS week_date " +
                            "    FROM DUAL " +
                            "    CONNECT BY LEVEL <= 5 " +
                            ") " +
                            "SELECT " +
                            "    TO_CHAR(w.week_date, 'YYYY-MM-DD') AS week_key, " +
                            "    'Wk ' || TO_CHAR(w.week_date, 'DD Mon') AS week_label, " +
                            "    COALESCE(SUM(i.total_amount), 0) AS revenue " +
                            "FROM weeks w " +
                            "LEFT JOIN invoice i " +
                            "    ON TRUNC(i.created_at, 'IW') = w.week_date " +
                            "    AND i.business_id = ? " +
                            "    AND i.status = 'PAID' " +
                            "GROUP BY w.week_date " +
                            "ORDER BY w.week_date ASC",
                    userId);
        } catch (Exception e) {
            /* ignore */ }
        model.addAttribute("weeklyRevenue", weeklyRevenue);

        List<Map<String, Object>> monthlyRevenue = jdbcTemplate.queryForList(
                "WITH months AS ( " +
                        "    SELECT ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -LEVEL + 1) AS month_date " +
                        "    FROM DUAL " +
                        "    CONNECT BY LEVEL <= 6 " +
                        ") " +
                        "SELECT " +
                        "    TO_CHAR(m.month_date, 'YYYY-MM') AS month, " +
                        "    TO_CHAR(m.month_date, 'Mon YYYY') AS month_label, " +
                        "    COALESCE(SUM(i.total_amount), 0) AS revenue " +
                        "FROM months m " +
                        "LEFT JOIN invoice i " +
                        "    ON TRUNC(i.created_at, 'MM') = m.month_date " +
                        "    AND i.business_id = ? " +
                        "    AND i.status = 'PAID' " +
                        "GROUP BY m.month_date " +
                        "ORDER BY m.month_date ASC",
                userId);
        model.addAttribute("monthlyRevenue", monthlyRevenue);

        // Quick scalar stats for the summary cards
        Double revenueToday = 0.0;
        Double revenueThisWeek = 0.0;
        try {
            revenueToday = jdbcTemplate.queryForObject(
                    "SELECT SUM(total_amount) FROM invoice WHERE business_id = ? AND status = 'PAID' AND TRUNC(created_at) = TRUNC(SYSDATE)",
                    Double.class, userId);
            if (revenueToday == null)
                revenueToday = 0.0;

            revenueThisWeek = jdbcTemplate.queryForObject(
                    "SELECT SUM(total_amount) FROM invoice WHERE business_id = ? AND status = 'PAID' AND TRUNC(created_at) >= TRUNC(SYSDATE) - 7",
                    Double.class, userId);
            if (revenueThisWeek == null)
                revenueThisWeek = 0.0;
        } catch (Exception e) {
        }
        model.addAttribute("revenueToday", revenueToday);
        model.addAttribute("revenueThisWeek", revenueThisWeek);

        // 5. Top Customers by Transactions (Top 5)
        List<Map<String, Object>> topCustomers = jdbcTemplate.queryForList(
                "SELECT customer_email, NVL(customer_name, 'Unknown') as name, SUM(total_amount) as total_spent, COUNT(id) as invoice_count "
                        +
                        "FROM invoice WHERE business_id = ? AND status = 'PAID' " +
                        "GROUP BY customer_email, customer_name ORDER BY total_spent DESC FETCH FIRST 5 ROWS ONLY",
                userId);
        model.addAttribute("topCustomers", topCustomers);

        return "business-analytics";
    }

    @PostMapping("/invoices/update")
    public String updateInvoice(@RequestParam("invoiceId") Long invoiceId, @RequestParam("status") String status,
            HttpSession session) {
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";
        try {
            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("update_invoice_status")
                    .declareParameters(
                            new SqlParameter("p_invoice_id", Types.NUMERIC),
                            new SqlParameter("p_new_status", Types.VARCHAR));
            jdbcCall.execute(new MapSqlParameterSource()
                    .addValue("p_invoice_id", invoiceId)
                    .addValue("p_new_status", status));

            // Notify customer about invoice update
            try {
                String customerEmail = jdbcTemplate.queryForObject(
                        "SELECT customer_email FROM invoice WHERE id = ?", String.class, invoiceId);
                Long businessId = jdbcTemplate.queryForObject(
                        "SELECT business_id FROM invoice WHERE id = ?", Long.class, invoiceId);
                String businessName = jdbcTemplate.queryForObject(
                        "SELECT full_name FROM users WHERE id = ?", String.class, businessId);

                Long searchId = null;
                try {
                    searchId = Long.parseLong(customerEmail);
                } catch (NumberFormatException ignored) {
                }

                Long customerId = jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE email = ? OR phone = ? OR id = ?", Long.class,
                        customerEmail, customerEmail, searchId);

                if (customerId != null) {
                    notificationService.sendNotification(customerId, "Invoice Updated",
                            "Your invoice from " + businessName + " has been marked as: " + status,
                            "ALERT", "/invoices/" + invoiceId);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
        }
        return "redirect:/business/dashboard";
    }

    // ═══════════════════════════════════════════════════
    // BUSINESS VERIFICATION DOCUMENTS
    // ═══════════════════════════════════════════════════

    /** Show the document submission form */
    @GetMapping("/verify")
    public String showVerificationForm(HttpSession session, Model model) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";

        Long userId = getUserId(session);
        try {
            Map<String, Object> user = jdbcTemplate.queryForMap(
                    "SELECT full_name, business_name, is_verified FROM users WHERE id = ?", userId);
            model.addAttribute("user", user);
        } catch (Exception e) {
            model.addAttribute("user", null);
        }
        return "business-verification";
    }

    /** Handle document submission */
    @PostMapping("/verify")
    public String submitVerificationDoc(
            @RequestParam("docType") String docType,
            @RequestParam(value = "docDescription", defaultValue = "") String docDescription,
            @RequestParam(value = "docFile", required = false) MultipartFile docFile,
            HttpSession session) {
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";

        Long userId = getUserId(session);
        try {
            String businessName = "";
            try {
                businessName = jdbcTemplate.queryForObject(
                        "SELECT NVL(business_name, full_name) FROM users WHERE id = ?", String.class, userId);
            } catch (Exception ignored) {
            }

            // Handle optional file upload
            String savedPath = null;
            if (docFile != null && !docFile.isEmpty()) {
                try {
                    Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
                    Files.createDirectories(uploadPath);
                    String originalName = docFile.getOriginalFilename();
                    String ext = (originalName != null && originalName.contains("."))
                            ? originalName.substring(originalName.lastIndexOf("."))
                            : "";
                    String fileName = "user" + userId + "_"
                            + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ext;
                    Files.copy(docFile.getInputStream(), uploadPath.resolve(fileName),
                            StandardCopyOption.REPLACE_EXISTING);
                    savedPath = uploadDir + "/" + fileName;
                } catch (Exception fileEx) {
                    System.err.println("[RevPay] File upload failed: " + fileEx.getMessage());
                }
            }

            jdbcTemplate.update(
                    "INSERT INTO business_verification_docs " +
                            "(user_id, business_name, doc_type, doc_description, doc_path, status, submitted_at) " +
                            "VALUES (?, ?, ?, ?, ?, 'PENDING', SYSTIMESTAMP)",
                    userId, businessName, docType, docDescription, savedPath);

            return "redirect:/business/verify/status?success=submitted";
        } catch (Exception e) {
            return "redirect:/business/verify?error=" + e.getMessage();
        }
    }

    /** Show status of all submissions for current business user */
    @GetMapping("/verify/status")
    public String verificationStatus(HttpSession session, Model model) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        if (!isBusinessUser(session))
            return "redirect:/dashboard?error=access_denied";

        Long userId = getUserId(session);
        List<Map<String, Object>> docs = jdbcTemplate.queryForList(
                "SELECT * FROM business_verification_docs WHERE user_id = ? ORDER BY submitted_at DESC", userId);
        model.addAttribute("docs", docs);

        // also pass verified status
        try {
            Integer isVerified = jdbcTemplate.queryForObject(
                    "SELECT is_verified FROM users WHERE id = ?", Integer.class, userId);
            model.addAttribute("isVerified", isVerified != null && isVerified == 1);
        } catch (Exception e) {
            model.addAttribute("isVerified", false);
        }
        return "verification-status";
    }

    /** ADMIN: list all pending verification submissions */
    @GetMapping("/admin/verifications")
    public String adminVerificationList(HttpSession session, Model model) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        String role = getUserRole(session);
        if (!"ROLE_ADMIN".equals(role))
            return "redirect:/dashboard?error=access_denied";

        List<Map<String, Object>> pending = jdbcTemplate.queryForList(
                "SELECT d.*, u.email as user_email, u.full_name " +
                        "FROM business_verification_docs d " +
                        "JOIN users u ON d.user_id = u.id " +
                        "ORDER BY d.submitted_at DESC");
        model.addAttribute("docs", pending);
        return "admin-verifications";
    }

    /** ADMIN: approve or reject a verification submission */
    @PostMapping("/admin/verifications/review")
    public String reviewVerification(
            @RequestParam("docId") Long docId,
            @RequestParam("decision") String decision,
            @RequestParam(value = "adminRemarks", defaultValue = "") String adminRemarks,
            HttpSession session) {
        String role = getUserRole(session);
        if (!"ROLE_ADMIN".equals(role))
            return "redirect:/dashboard?error=access_denied";

        try {
            // Update the doc record
            jdbcTemplate.update(
                    "UPDATE business_verification_docs " +
                            "SET status = ?, admin_remarks = ?, reviewed_at = SYSTIMESTAMP " +
                            "WHERE id = ?",
                    decision.toUpperCase(), adminRemarks, docId);

            // If approved, mark the user as verified
            if ("APPROVED".equalsIgnoreCase(decision)) {
                Long userId = jdbcTemplate.queryForObject(
                        "SELECT user_id FROM business_verification_docs WHERE id = ?", Long.class, docId);
                if (userId != null) {
                    jdbcTemplate.update("UPDATE users SET is_verified = 1 WHERE id = ?", userId);

                    // Notify the business user
                    try {
                        notificationService.sendNotification(userId,
                                "Account Verified",
                                "Congratulations! Your business account has been verified successfully.",
                                "ALERT", "/business/verify/status");
                    } catch (Exception ignored) {
                    }
                }
            }
            return "redirect:/business/admin/verifications?success=reviewed";
        } catch (Exception e) {
            return "redirect:/business/admin/verifications?error=" + e.getMessage();
        }
    }
}
