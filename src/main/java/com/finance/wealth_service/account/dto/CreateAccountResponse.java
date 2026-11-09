package com.finance.wealth_service.account.dto;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.entity.Account;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountResponse(UUID id, UUID userId, String name, BigDecimal currentAmount, AccountType type) {
    public static CreateAccountResponse from(Account account) {
        return new CreateAccountResponse(account.getId(), account.getUserId(), account.getName(),
                account.getCurrentAmount(), account.getType());
    }
}
