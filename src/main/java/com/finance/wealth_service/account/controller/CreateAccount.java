package com.finance.wealth_service.account.controller;

import com.finance.wealth_service.account.actions.CreateAccountAction;
import com.finance.wealth_service.account.dto.CreateAccountRequest;
import com.finance.wealth_service.account.dto.CreateAccountResponse;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Accounts", description = "Financial accounts")
public class CreateAccount {
    private final CreateAccountAction action;

    public CreateAccount(CreateAccountAction action) {
        this.action = action;
    }

    @PostMapping("/accounts")
    @Operation(summary = "Create an account", description = "Creates an account with an optional initial balance.")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = action.execute(request);
        return new CreateAccountResponse(account.getId(), account.getUserId(), account.getName(),
                account.getCurrentAmount(), account.getType());
    }

    @GetMapping("/whoami")
    public AuthenticatedUser whoami(@AuthenticationPrincipal AuthenticatedUser user) {
        return user;
    }

}
