package com.revpay.repository;

import com.revpay.model.MoneyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MoneyRequestRepository extends JpaRepository<MoneyRequest, Long> {
    List<MoneyRequest> findByRequesterIdOrRequesteeIdOrderByCreatedAtDesc(Long requesterId, Long requesteeId);
}
