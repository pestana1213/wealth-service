package com.finance.wealth_service.transaction.repository;

import com.finance.wealth_service.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query(value = """
            SELECT t.* FROM transactions t
            JOIN users u ON u.id = t.user_id
            WHERE u.subject = :subject AND u.issuer = :issuer
            ORDER BY t.occurred_at DESC, t.created_at DESC, t.id DESC""",
            nativeQuery = true)
    List<Transaction> findHistory(@Param("subject") String subject, @Param("issuer") String issuer);

    boolean existsByReversalOf(UUID reversalOf);

    @Query(value = """
            SELECT t.* FROM transactions t
            JOIN users u ON u.id = t.user_id
            WHERE t.id = :transactionId AND u.subject = :subject AND u.issuer = :issuer""",
            nativeQuery = true)
    Optional<Transaction> findOwnedBy(@Param("transactionId") UUID transactionId,
                                      @Param("subject") String subject,
                                      @Param("issuer") String issuer);
}
