package com.finance.wealth_service.account.dto;

import com.finance.wealth_service.account.AccountType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Omitted fields are left unchanged. The account is named by the path. */
public record UpdateAccountRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") @Size(max = 255) String name,
        AccountType type) {}
