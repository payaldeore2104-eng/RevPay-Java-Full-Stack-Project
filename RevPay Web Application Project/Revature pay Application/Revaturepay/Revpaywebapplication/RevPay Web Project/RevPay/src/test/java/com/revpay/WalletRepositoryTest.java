package com.revpay;

import com.revpay.model.Wallet;
import com.revpay.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
public class WalletRepositoryTest {

    @Autowired
    private WalletRepository walletRepository;

    @Test
    public void testFindByUserId_NotFound() {
        // Assume User ID 999999 does not exist
        Optional<Wallet> walletOpt = walletRepository.findByUserId(999999L);
        assertFalse(walletOpt.isPresent(), "Wallet should not be found for non-existent User ID");
    }
}
