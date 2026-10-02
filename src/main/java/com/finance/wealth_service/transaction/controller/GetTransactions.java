package com.finance.wealth_service.transaction.controller;

import com.finance.wealth_service.transaction.actions.ListTransactions;
import com.finance.wealth_service.transaction.dto.TransactionDTO;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    @GetMapping(value = "/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all transactions",
            description = "Newest first, by movement date and then by insertion order.")
    @ResponseStatus(HttpStatus.OK)
    public List<TransactionDTO> listTransactions(@RequestParam UUID userId) {
        return listTransactions.listTransactions(userId);
    }

}
