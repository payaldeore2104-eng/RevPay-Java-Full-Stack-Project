package com.revpay.admin.dto;

import java.math.BigDecimal;

public class AdminStatsDto {
    private Long totalUsers;
    private Long totalTransactions;
    private BigDecimal totalMoneyTransferred;
    private Long activeUsers;

    public Long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(Long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public Long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(Long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public BigDecimal getTotalMoneyTransferred() {
        return totalMoneyTransferred;
    }

    public void setTotalMoneyTransferred(BigDecimal totalMoneyTransferred) {
        this.totalMoneyTransferred = totalMoneyTransferred;
    }

    public Long getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Long activeUsers) {
        this.activeUsers = activeUsers;
    }
}

