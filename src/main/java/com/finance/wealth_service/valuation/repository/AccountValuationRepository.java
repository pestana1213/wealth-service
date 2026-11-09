package com.finance.wealth_service.valuation.repository;

import com.finance.wealth_service.valuation.entity.AccountValuation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountValuationRepository extends JpaRepository<AccountValuation, UUID> {

    @Query(value = """
            SELECT * FROM account_valuations
            WHERE account_id = :accountId
              AND (CAST(:from AS date) IS NULL OR valued_at >= :from)
              AND (CAST(:to AS date) IS NULL OR valued_at <= :to)
            ORDER BY valued_at DESC
            """, nativeQuery = true)
    List<AccountValuation> findForAccountBetween(@Param("accountId") UUID accountId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    boolean existsByAccountIdAndValuedAt(UUID accountId, LocalDate valuedAt);

    @Query(value = """
            SELECT DISTINCT ON (account_id) * FROM account_valuations
            WHERE account_id IN (:accountIds)
            ORDER BY account_id, valued_at DESC, created_at DESC
            """, nativeQuery = true)
    List<AccountValuation> findLatestPerAccount(@Param("accountIds") Collection<UUID> accountIds);
}
