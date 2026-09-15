package com.finance.wealth_service.transaction.controller;

import com.finance.wealth_service.transaction.actions.CreateTransactionAction;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.dto.CreateTransactionResponse;
import com.finance.wealth_service.transaction.entity.Transaction;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Transactions", description = "Income, expenses and transfers")
public class CreateTransaction {
    private final CreateTransactionAction action;

    public CreateTransaction(CreateTransactionAction action) {
        this.action = action;
    }

    @PostMapping("/transactions")
    @Operation(summary = "Create a transaction", description = "Records an income, expense or transfer and updates affected balances.")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTransactionResponse create(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction transaction = action.execute(request);
        return new CreateTransactionResponse(
                transaction.getId(), transaction.getUserId(), transaction.getProviderAccountId(),
                transaction.getDestinationAccountId(), transaction.getSourceBucketId(),
                transaction.getDestinationBucketId(), transaction.getTransactionAmount(),
                transaction.getType(), transaction.getCategory(), transaction.getOccurredAt(),
                transaction.getDescription());
    }
}
