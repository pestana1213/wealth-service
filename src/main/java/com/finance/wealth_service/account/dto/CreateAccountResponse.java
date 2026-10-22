package com.finance.wealth_service.account.dto;

import com.finance.wealth_service.account.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountResponse(UUID id, UUID userId, String name, BigDecimal currentAmount, AccountType type) {}
