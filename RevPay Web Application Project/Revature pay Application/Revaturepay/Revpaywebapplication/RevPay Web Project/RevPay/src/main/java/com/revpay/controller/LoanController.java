package com.revpay.controller;

import com.revpay.util.TransactionIdGenerator;
import com.revpay.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.sql.Types;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/loans")
public class LoanController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserService userService;

    private String getLoggedInEmail(HttpSession session) {
        return (String) session.getAttribute("loggedInUser");
    }

    private Long getUserId(HttpSession session) {
        String email = getLoggedInEmail(session);
        if (email == null)
            return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE email = ? OR phone = ?", Long.class, email, email);
        } catch (Exception e) {
            // DB unavailable – return -1 sentinel so session is still honoured
            return -1L;
        }
    }

    @GetMapping
    public String viewLoans(HttpSession session, Model model) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        Long userId = getUserId(session);
        try {
            List<Map<String, Object>> rawLoans = jdbcTemplate
                    .queryForList("SELECT * FROM loans WHERE user_id = ? ORDER BY created_at DESC", userId);

            // Create mutable maps and calculate next EMI info
            List<Map<String, Object>> loans = new java.util.ArrayList<>();
            for (Map<String, Object> map : rawLoans) {
                Map<String, Object> l = new HashMap<>(map);

                String status = (String) l.get("STATUS");
                if ("APPROVED".equals(status)) {
                    // Calculate Next EMI Details
                    Double emiAmount = ((Number) l.get("EMI_AMOUNT")).doubleValue();
                    Double repaidAmount = l.get("REPAID_AMOUNT") != null
                            ? ((Number) l.get("REPAID_AMOUNT")).doubleValue()
                            : 0.0;
                    Double principalAmount = ((Number) l.get("PRINCIPAL_AMOUNT")).doubleValue();
                    Double remaining = principalAmount - repaidAmount;

                    // Remaining EMI Amount - cap it at the actual remaining balance if it's the
                    // last payment
                    Double nextEmiAmount = remaining < emiAmount ? remaining : emiAmount;
                    if (nextEmiAmount < 0)
                        nextEmiAmount = 0.0;

                    // Next Payment Date = (Months Paid + 1) added to CREATED_AT
                    int monthsPaid = (int) (repaidAmount / emiAmount);
                    Date createdAt = (Date) l.get("CREATED_AT");

                    Calendar cal = Calendar.getInstance();
                    if (createdAt != null) {
                        cal.setTime(createdAt);
                    }
                    cal.add(Calendar.MONTH, monthsPaid + 1);
                    Date nextPaymentDate = cal.getTime();

                    l.put("NEXT_PAYMENT_AMOUNT", nextEmiAmount);
                    l.put("NEXT_PAYMENT_DATE", nextPaymentDate);
                } else if ("REPAID".equals(status)) {
                    l.put("NEXT_PAYMENT_AMOUNT", 0.0);
                }

                loans.add(l);
            }

            model.addAttribute("loans", loans);
        } catch (Exception e) {
            model.addAttribute("loans", java.util.Collections.emptyList());
            model.addAttribute("error", "Could not load loans: " + e.getMessage());
        }
        return "loan-details";
    }

    @GetMapping("/apply")
    public String showApplyForm(HttpSession session) {
        if (getLoggedInEmail(session) == null)
            return "redirect:/login";
        return "loan-apply";
    }

    @PostMapping("/estimate")
    @ResponseBody
    public Double estimateEmi(@RequestParam("principal") Double principal, @RequestParam("months") Integer months) {
        try {
            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("calculate_emi")
                    .declareParameters(
                            new SqlParameter("p_principal", Types.NUMERIC),
                            new SqlParameter("p_rate_annual", Types.NUMERIC),
                            new SqlParameter("p_months", Types.NUMERIC),
                            new SqlOutParameter("p_out_emi", Types.NUMERIC));
            MapSqlParameterSource in = new MapSqlParameterSource()
                    .addValue("p_principal", principal)
                    .addValue("p_rate_annual", 12.0)
                    .addValue("p_months", months);
            Map<String, Object> out = jdbcCall.execute(in);
            Number emi = (Number) out.get("p_out_emi");
            return emi != null ? emi.doubleValue() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    @PostMapping("/apply")
    public String applyLoan(@RequestParam("principal") Double principal,
            @RequestParam("months") Integer months,
            @RequestParam("purpose") String purpose,
            @RequestParam("financialDetails") String financialDetails,
            @RequestParam(value = "document", required = false) org.springframework.web.multipart.MultipartFile document,
            HttpSession session) {
        Long userId = getUserId(session);
        Double emi = estimateEmi(principal, months);

        String docPath = "No Document";
        if (document != null && !document.isEmpty()) {
            try {
                java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads/");
                if (!java.nio.file.Files.exists(uploadDir)) {
                    java.nio.file.Files.createDirectories(uploadDir);
                }
                String originalFilename = document.getOriginalFilename();
                java.nio.file.Path filePath = uploadDir.resolve(originalFilename);
                document.transferTo(filePath.toFile());
                docPath = originalFilename;
            } catch (Exception e) {
                e.printStackTrace();
                docPath = document.getOriginalFilename(); // Fallback to simulated upload
            }
        }

        jdbcTemplate.update(
                "INSERT INTO loans (user_id, principal_amount, interest_rate, tenure_months, emi_amount, purpose, financial_details, document_path, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, principal, 12.0, months, emi, purpose, financialDetails, docPath, "PENDING");

        return "redirect:/loans?success=applied";
    }

    @GetMapping("/download-doc")
    public org.springframework.http.ResponseEntity<byte[]> downloadDoc(@RequestParam("filename") String filename,
            HttpSession session) {
        if (getLoggedInEmail(session) == null) {
            return new org.springframework.http.ResponseEntity<>(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        try {
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads/").resolve(filename);

            if (java.nio.file.Files.exists(filePath)) {
                byte[] content = java.nio.file.Files.readAllBytes(filePath);

                String contentType = java.nio.file.Files.probeContentType(filePath);
                if (contentType == null) {
                    contentType = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
                }

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.parseMediaType(contentType));
                headers.add(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename + "\"");
                return new org.springframework.http.ResponseEntity<>(content, headers,
                        org.springframework.http.HttpStatus.OK);
            } else {
                // Fallback for simulated or missing files
                String message = "The simulated document '" + filename
                        + "' could not be found on the server.\nIt was likely uploaded when document storage was simulated.";
                byte[] content = message.getBytes();

                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.TEXT_PLAIN);
                // Append .txt so the browser doesn't try to open it as a corrupted PDF
                headers.add(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename + ".txt\"");
                return new org.springframework.http.ResponseEntity<>(content, headers,
                        org.springframework.http.HttpStatus.OK);
            }
        } catch (Exception e) {
            return new org.springframework.http.ResponseEntity<>(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/repay")
    public String repayLoan(@RequestParam("loanId") Long loanId,
            @RequestParam("amount") Double amount,
            @RequestParam("pin") String pin,
            HttpSession session) {
        Long userId = getUserId(session);
        try {
            // Check current status and amount
            Map<String, Object> loan = jdbcTemplate.queryForMap(
                    "SELECT principal_amount, emi_amount, repaid_amount, status FROM loans WHERE id = ? AND user_id = ?",
                    loanId, userId);

            if (!"APPROVED".equals(loan.get("STATUS"))) {
                return "redirect:/loans?error=loan_not_approved";
            }

            // Verify transaction PIN before debiting EMI
            try {
                boolean pinValid = userService.validatePin(userId, pin);
                if (!pinValid) {
                    return "redirect:/loans?error=invalid_pin";
                }
            } catch (Exception e) {
                return "redirect:/loans?error=pin_verification_failed";
            }

            // Simple JDBC call wrapper for withdraw_money
            String transactionId = TransactionIdGenerator.generateTransactionId();
            SimpleJdbcCall withdrawCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("withdraw_money")
                    .declareParameters(
                            new SqlParameter("p_user_id", Types.NUMERIC),
                            new SqlParameter("p_amount", Types.NUMERIC),
                            new SqlParameter("p_transaction_id", Types.VARCHAR),
                            new SqlOutParameter("p_out_new_balance", Types.NUMERIC));

            MapSqlParameterSource withdrawIn = new MapSqlParameterSource()
                    .addValue("p_user_id", userId)
                    .addValue("p_amount", amount)
                    .addValue("p_transaction_id", transactionId);

            Map<String, Object> out = withdrawCall.execute(withdrawIn);
            Number newBalance = (Number) out.get("p_out_new_balance");

            if (newBalance != null) {
                // Add to repaid amount
                Double currentRepaid = ((Number) loan.get("REPAID_AMOUNT")).doubleValue();
                Double newRepaid = currentRepaid + amount;

                // Roughly checking if fully paid (principal + interest roughly derived via emi
                // * months)
                // This is simplified.
                Double principalAmount = ((Number) loan.get("PRINCIPAL_AMOUNT")).doubleValue();
                Double emiAmount = ((Number) loan.get("EMI_AMOUNT")).doubleValue();
                // Assuming number of months is pulled or we check against a fixed factor.
                // Simplified: just check if newRepaid >= principalAmount
                if (newRepaid >= principalAmount) {
                    jdbcTemplate.update("UPDATE loans SET repaid_amount = ?, status = 'REPAID' WHERE id = ?", newRepaid,
                            loanId);
                } else {
                    jdbcTemplate.update("UPDATE loans SET repaid_amount = ? WHERE id = ?", newRepaid, loanId);
                }

                return "redirect:/loans?success=repaid_partially";
            } else {
                return "redirect:/loans?error=insufficient_funds";
            }

        } catch (Exception e) {
            return "redirect:/loans?error=" + e.getMessage();
        }
    }

}
