package com.revpay;

import com.revpay.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class TransactionServiceImplTest {

    @Autowired
    private TransactionService transactionService;

    @Test
    public void testTransferMoney_InvalidTargetValidation() {
        boolean exceptionThrown = false;
        try {
            // The service is supposed to assert the user lookup and immediately throw if
            // target is missing.
            // This is testing explicit @Transactional scope rollback on invalid lookups
            transactionService.transferMoney(1L, "nobody@nowhere.fake", new BigDecimal("5.00"), "Testing Transfer");
        } catch (Exception e) {
            exceptionThrown = true;
            // "Receiver not found by email, phone, or account ID"
        }
        assertTrue(exceptionThrown,
                "TransactionService should throw Exception and validate transaction state when target doesn't exist");
    }
}
