package com.finance.wealth_service.valuation.repository;

import com.finance.wealth_service.valuation.entity.AccountValuation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AccountValuationRepository extends JpaRepository<AccountValuation, UUID> {

    List<AccountValuation> findByAccountIdOrderByValuedAtDesc(UUID accountId);

    boolean existsByAccountIdAndValuedAt(UUID accountId, LocalDate valuedAt);
}
