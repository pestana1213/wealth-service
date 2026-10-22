package com.finance.wealth_service.valuation.controller;

import com.finance.wealth_service.valuation.actions.CreateValuationAction;
import com.finance.wealth_service.valuation.dto.CreateValuationRequest;
import com.finance.wealth_service.valuation.dto.ValuationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Valuations", description = "Market value history of investment accounts")
public class CreateValuation {
    private final CreateValuationAction action;

    public CreateValuation(CreateValuationAction action) {
        this.action = action;
    }

    @PostMapping("/accounts/{accountId}/valuations")
    @Operation(summary = "Record a valuation",
            description = "Records the market value of an investment account on a date. One valuation per account per date. Does not change the account balance.")
    @ResponseStatus(HttpStatus.CREATED)
    public ValuationResponse create(@PathVariable UUID accountId, @Valid @RequestBody CreateValuationRequest request) {
        return ValuationResponse.from(action.execute(accountId, request));
    }
}
