package com.finance.wealth_service.valuation.dto;

import com.finance.wealth_service.valuation.entity.AccountValuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ValuationResponse(UUID id, UUID accountId, BigDecimal value, BigDecimal invested, LocalDate valuedAt) {

    public static ValuationResponse from(AccountValuation valuation) {
        return new ValuationResponse(valuation.getId(), valuation.getAccountId(), valuation.getValue(), valuation.getInvestedMoney(),valuation.getValuedAt());
    }
}
