package com.finance.wealth_service.transaction.controller;

import com.finance.wealth_service.transaction.actions.ListTransactions;
import com.finance.wealth_service.transaction.entity.Transaction;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Transactions", description = "See all transactions")
public class GetTransactions {

    private final ListTransactions listTransactions;

    public GetTransactions(ListTransactions listTransactions) {
        this.listTransactions = listTransactions;
    }

    @Timed
    @GetMapping("/transactions")
    @Operation(summary = "List all transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Transaction> listTransactions(@RequestParam UUID userId) {
        return listTransactions.listTransactions(userId);
    }

}
