package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.ListRecurringAction;
import com.finance.wealth_service.recurring.dto.RecurringResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
public class GetRecurring {

    private final ListRecurringAction action;

    public GetRecurring(ListRecurringAction action) {
        this.action = action;
    }

    @GetMapping("/v1/recurring")
    @Operation(summary = "List recurring movements",
            description = "All of the caller's recurring movements, soonest next occurrence first; finished ones last.")
    public List<RecurringResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return action.execute(user).stream().map(RecurringResponse::from).toList();
    }
}
