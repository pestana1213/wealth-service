package com.finance.wealth_service.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountResponse(UUID id, UUID userId, String name, BigDecimal currentAmount) {}
