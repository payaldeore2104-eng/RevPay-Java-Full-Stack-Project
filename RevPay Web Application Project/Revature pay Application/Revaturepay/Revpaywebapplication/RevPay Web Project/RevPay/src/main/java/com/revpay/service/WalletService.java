package com.revpay.service;

import com.revpay.model.BankAccount;
import com.revpay.model.Card;
import com.revpay.model.Wallet;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
        Wallet getWalletByUserId(Long userId);

        BigDecimal addMoney(Long userId, BigDecimal amount, String sourceType, Long sourceId, String cardPin)
                        throws Exception;

        BigDecimal withdrawMoney(Long userId, BigDecimal amount, String destinationType, Long destinationId,
                        String cardPin)
                        throws Exception;

        // Cards
        // Card Management
        List<Card> getCardsByUserId(Long userId);

        void addCard(Long userId, String cardNumber, String cardHolder, String expiryDate, String cvv,
                        String billingAddress, String cardPin, boolean isDefault, String cardType)
                        throws Exception;

        void editCard(Long cardId, String cardHolder, String expiryDate) throws Exception;

        void removeCard(Long cardId) throws Exception;

        void setDefaultCard(Long userId, Long cardId) throws Exception;

        // Bank Account Management
        List<BankAccount> getBankAccountsByUserId(Long userId);

        void addBankAccount(Long userId, String accountNumber, String routingNumber, String bankName,
                        String accountType)
                        throws Exception;

        void removeBankAccount(Long accountId) throws Exception;

 
        java.util.List<java.util.Map<String, Object>> getWalletTransactions(Long userId, int limit);
}
