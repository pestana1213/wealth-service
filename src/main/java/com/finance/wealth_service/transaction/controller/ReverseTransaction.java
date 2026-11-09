package com.finance.wealth_service.transaction.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.transaction.actions.ReverseTransactionAction;
import com.finance.wealth_service.transaction.dto.CreateTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Transactions", description = "Income, expenses and transfers")
public class ReverseTransaction {
    private final ReverseTransactionAction action;

    public ReverseTransaction(ReverseTransactionAction action) {
        this.action = action;
    }

    @PostMapping("/transactions/{transactionId}/reversal")
    @Operation(summary = "Reverse a transaction",
            description = "Records a compensating transaction that undoes the balance effect of the original and links "
                    + "back to it. The original is kept. Reversing twice, or reversing a reversal, returns 409.")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTransactionResponse reverse(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID transactionId) {
        return CreateTransactionResponse.from(action.execute(user, transactionId));
    }
}
