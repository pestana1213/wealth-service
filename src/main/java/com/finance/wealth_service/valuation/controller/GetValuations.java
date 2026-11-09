package com.finance.wealth_service.valuation.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.valuation.actions.ListValuationsAction;
import com.finance.wealth_service.valuation.dto.ValuationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Valuations", description = "Market value history of investment accounts")
public class GetValuations {
    private final ListValuationsAction action;

    public GetValuations(ListValuationsAction action) {
        this.action = action;
    }

    @GetMapping("/accounts/{accountId}/valuations")
    @Operation(summary = "List valuations", description = "Newest valuation date first.")
    public List<ValuationResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID accountId) {
        //TODO: ADD PAGINATION
        return action.execute(user, accountId).stream().map(ValuationResponse::from).toList();
    }
}
