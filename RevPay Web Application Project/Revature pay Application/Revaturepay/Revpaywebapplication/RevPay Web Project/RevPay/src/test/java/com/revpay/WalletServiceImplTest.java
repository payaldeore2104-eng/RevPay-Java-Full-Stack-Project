package com.revpay;

import com.revpay.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class WalletServiceImplTest {

    @Autowired
    private WalletService walletService;

    @Test
    public void testWithdrawMoney_InsufficientFundsValidation() {
        boolean threwException = false;
        try {
       
            walletService.withdrawMoney(999999L, new BigDecimal("1000000.00"), "BANK", 0L, null);
        } catch (Exception e) {
            threwException = true;

        }
        assertTrue(threwException,
                "Service should enforce transaction rollback or throw exception when withdrawing without funds");
    }
}
