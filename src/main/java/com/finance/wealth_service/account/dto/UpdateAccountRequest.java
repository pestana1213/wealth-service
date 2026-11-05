package com.finance.wealth_service.account.dto;

import com.finance.wealth_service.account.AccountType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateAccountRequest(
        UUID accountId,
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 255) String name,
        AccountType type) {}
