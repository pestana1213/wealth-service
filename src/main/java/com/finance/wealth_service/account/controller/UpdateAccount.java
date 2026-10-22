package com.finance.wealth_service.account.controller;

import com.finance.wealth_service.account.actions.UpdateAccountAction;
import com.finance.wealth_service.account.dto.CreateAccountResponse;
import com.finance.wealth_service.account.dto.UpdateAccountRequest;
import com.finance.wealth_service.account.entity.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Accounts", description = "Financial accounts")
public class UpdateAccount {
    private final UpdateAccountAction action;

    public UpdateAccount(UpdateAccountAction action) {
        this.action = action;
    }

    @PatchMapping("/accounts/{accountId}")
    @Operation(summary = "Update an account",
            description = "Updates the account name and/or type. Omitted fields are left unchanged. Balance changes go through transactions.")
    public CreateAccountResponse updateAccount(@PathVariable UUID accountId, @Valid @RequestBody UpdateAccountRequest request) {
        Account account = action.execute(accountId, request);
        return new CreateAccountResponse(account.getId(), account.getUserId(), account.getName(),
                account.getCurrentAmount(), account.getType());
    }
}
