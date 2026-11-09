package com.finance.wealth_service.transaction.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.common.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.transaction.actions.ListTransactions;
import com.finance.wealth_service.transaction.dto.TransactionDTO;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;


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
    public PageResponse<TransactionDTO> listTransactions(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestParam(defaultValue = "0") @PositiveOrZero int page) {
        return listTransactions.listTransactions(user, page);
    }

}
