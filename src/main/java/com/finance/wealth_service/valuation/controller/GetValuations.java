package com.finance.wealth_service.valuation.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.valuation.actions.ListValuationsAction;
import com.finance.wealth_service.valuation.dto.ValuationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@RestController
@Tag(name = "Valuations", description = "Market value history of investment accounts")
public class GetValuations {
    private final ListValuationsAction action;

    public GetValuations(ListValuationsAction action) {
        this.action = action;
    }

    @GetMapping("/accounts/{accountId}/valuations")
    @Operation(summary = "List valuations", description = "Newest valuation date first. Optional from/to (yyyy-MM-dd, inclusive) narrow the range.")
    public List<ValuationResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable UUID accountId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate to) {
        return action.execute(user, accountId, from, to).stream().map(ValuationResponse::from).toList();
    }
}
