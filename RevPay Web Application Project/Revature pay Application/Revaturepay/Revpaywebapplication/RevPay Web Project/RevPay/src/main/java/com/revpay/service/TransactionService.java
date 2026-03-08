package com.revpay.service;

import com.revpay.model.MoneyRequest;
import com.revpay.model.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface TransactionService {
        Long transferMoney(Long senderId, String receiverEmailOrPhone, BigDecimal amount, String description)
                        throws Exception;

        Long createRequest(Long requesterId, String requesteeEmailOrPhone, BigDecimal amount, String description)
                        throws Exception;

        void updateRequestStatus(Long requestId, Long userId, String newStatus) throws Exception;

        List<Transaction> getUserTransactions(Long userId);

        List<MoneyRequest> getUserRequests(Long userId);


        List<Map<String, Object>> getUserTransactionsJdbc(Long userId, String type, String status, String startDate,
                        String endDate, BigDecimal minAmount, BigDecimal maxAmount, String searchQuery);

        List<Map<String, Object>> getUserRequestsJdbc(Long userId);
}
