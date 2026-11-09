package com.finance.wealth_service.transaction.repository;

import com.finance.wealth_service.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query(value = """
            SELECT t.* FROM transactions t
            JOIN users u ON u.id = t.user_id
            WHERE u.subject = :subject AND u.issuer = :issuer
            ORDER BY t.occurred_at DESC, t.created_at DESC, t.id DESC
            LIMIT :limit OFFSET :offset""",
            nativeQuery = true)
    List<Transaction> findHistory(@Param("subject") String subject,
                                  @Param("issuer") String issuer,
                                  @Param("limit") int limit,
                                  @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM transactions t
            JOIN users u ON u.id = t.user_id
            WHERE u.subject = :subject AND u.issuer = :issuer""",
            nativeQuery = true)
    long countHistory(@Param("subject") String subject, @Param("issuer") String issuer);

    /**
     * Money that entered the account minus money that left it, over movements dated
     * after {@code date}. Subtracted from today's balance, it gives the balance at the
     * end of {@code date}. A move between two buckets of the account counts both ways
     * and nets to zero.
     */
    @Query(value = """
            SELECT COALESCE(
                       SUM(CASE WHEN destination_account_id = :accountId THEN transaction_amount ELSE 0 END)
                     - SUM(CASE WHEN provider_account_id = :accountId THEN transaction_amount ELSE 0 END), 0)
            FROM transactions
            WHERE (destination_account_id = :accountId OR provider_account_id = :accountId)
              AND occurred_at > :date""",
            nativeQuery = true)
    BigDecimal netInflowAfter(@Param("accountId") UUID accountId, @Param("date") LocalDate date);

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
