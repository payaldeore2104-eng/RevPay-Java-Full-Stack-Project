package com.revpay.admin.service;

import com.revpay.admin.dto.AdminStatsDto;
import com.revpay.admin.dto.AdminLoanDto;
import com.revpay.admin.dto.AdminTransactionDto;
import com.revpay.admin.dto.AdminUserDto;
import com.revpay.admin.repository.AdminRepository;
import com.revpay.util.TransactionIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AdminServiceImpl(AdminRepository adminRepository, JdbcTemplate jdbcTemplate) {
        this.adminRepository = adminRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AdminUserDto> getAllUsers(String searchQuery) {
        return adminRepository.findAllUsers(searchQuery);
    }

    @Override
    public void blockUser(Long userId) {
        adminRepository.setUserBlocked(userId, true);
    }

    @Override
    public void unblockUser(Long userId) {
        adminRepository.setUserBlocked(userId, false);
    }

    @Override
    public List<AdminTransactionDto> getAllTransactions(String searchQuery, String status, String type,
            String startDate, String endDate) {
        return adminRepository.findAllTransactions(searchQuery, status, type, startDate, endDate);
    }

    @Override
    public AdminStatsDto getSystemStats() {
        return adminRepository.getSystemStats();
    }

    @Override
    public List<AdminLoanDto> getAllLoanRequests() {
        return adminRepository.findAllLoans();
    }

    @Override
    public void approveLoan(Long loanId) {
        // Fetch loan to avoid double-crediting wallet
        java.util.Map<String, Object> loan;
        try {
            loan = jdbcTemplate.queryForMap(
                    "SELECT user_id, principal_amount, status FROM loans WHERE id = ?",
                    loanId);
        } catch (Exception e) {
            adminRepository.updateLoanStatus(loanId, "APPROVED");
            return;
        }

        Long userId = loan.get("USER_ID") != null ? ((Number) loan.get("USER_ID")).longValue() : null;
        BigDecimal principal = loan.get("PRINCIPAL_AMOUNT") != null
                ? new BigDecimal(((Number) loan.get("PRINCIPAL_AMOUNT")).toString())
                : BigDecimal.ZERO;
        String status = loan.get("STATUS") != null ? loan.get("STATUS").toString() : "";

        // Only credit wallet once, when moving from non-approved to APPROVED
        if (userId != null && principal.compareTo(BigDecimal.ZERO) > 0 && !"APPROVED".equals(status)) {
            try {
                String txId = TransactionIdGenerator.generateTransactionId();
                SimpleJdbcCall addMoneyCall = new SimpleJdbcCall(jdbcTemplate)
                        .withProcedureName("add_money")
                        .declareParameters(
                                new SqlParameter("p_user_id", Types.NUMERIC),
                                new SqlParameter("p_amount", Types.NUMERIC),
                                new SqlParameter("p_transaction_id", Types.VARCHAR),
                                new SqlOutParameter("p_out_new_balance", Types.NUMERIC));
                MapSqlParameterSource in = new MapSqlParameterSource()
                        .addValue("p_user_id", userId)
                        .addValue("p_amount", principal)
                        .addValue("p_transaction_id", txId);
                addMoneyCall.execute(in);
            } catch (Exception ignored) {
            }
        }

        adminRepository.updateLoanStatus(loanId, "APPROVED");
    }

    @Override
    public void rejectLoan(Long loanId) {
        adminRepository.updateLoanStatus(loanId, "REJECTED");
    }
}

