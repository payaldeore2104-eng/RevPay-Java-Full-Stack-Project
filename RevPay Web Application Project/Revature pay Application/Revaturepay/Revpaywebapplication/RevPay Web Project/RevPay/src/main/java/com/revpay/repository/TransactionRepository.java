package com.revpay.repository;

import com.revpay.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(Long senderId, Long receiverId);
}
