package com.finance.wealth_service.transaction.repository;

import com.finance.wealth_service.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByUserIdOrderByOccurredAtDesc(UUID userId);
}
