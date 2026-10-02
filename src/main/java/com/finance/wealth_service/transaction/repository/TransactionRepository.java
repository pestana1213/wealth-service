package com.finance.wealth_service.transaction.repository;

import com.finance.wealth_service.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("""
            select t from Transaction t
            where t.userId = :userId
            order by t.occurredAt desc, t.createdAt desc, t.id desc
            """)
    List<Transaction> findHistory(@Param("userId") UUID userId);
}
