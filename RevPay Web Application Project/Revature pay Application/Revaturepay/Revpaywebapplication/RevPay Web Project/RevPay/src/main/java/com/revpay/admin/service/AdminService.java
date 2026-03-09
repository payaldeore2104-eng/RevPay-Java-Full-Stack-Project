package com.revpay.admin.service;

import com.revpay.admin.dto.AdminStatsDto;
import com.revpay.admin.dto.AdminLoanDto;
import com.revpay.admin.dto.AdminTransactionDto;
import com.revpay.admin.dto.AdminUserDto;

import java.util.List;

public interface AdminService {
    List<AdminUserDto> getAllUsers(String searchQuery);

    void blockUser(Long userId);

    void unblockUser(Long userId);

    List<AdminTransactionDto> getAllTransactions(String searchQuery, String status, String type, String startDate,
            String endDate);

    AdminStatsDto getSystemStats();

    List<AdminLoanDto> getAllLoanRequests();

    void approveLoan(Long loanId);

    void rejectLoan(Long loanId);
}

