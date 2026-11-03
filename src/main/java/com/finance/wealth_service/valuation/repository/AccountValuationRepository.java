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

    List<AccountValuation> findByAccountIdOrderByValuedAtDesc(UUID accountId);

    boolean existsByAccountIdAndValuedAt(UUID accountId, LocalDate valuedAt);

    @Query(value = """
            SELECT DISTINCT ON (account_id) * FROM account_valuations
            WHERE account_id IN (:accountIds)
            ORDER BY account_id, valued_at DESC, created_at DESC
            """, nativeQuery = true)
    List<AccountValuation> findLatestPerAccount(@Param("accountIds") Collection<UUID> accountIds);
}
