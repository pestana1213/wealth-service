package com.finance.wealth_service.account.dto;

import com.finance.wealth_service.account.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(
        @NotNull UUID userId,
        @NotBlank @Size(max = 255) String name,
        @Digits(integer = 17, fraction = 2) BigDecimal currentAmount,
        AccountType type) {}
