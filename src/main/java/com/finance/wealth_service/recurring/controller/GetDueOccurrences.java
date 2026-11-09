package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.ListDueOccurrencesAction;
import com.finance.wealth_service.recurring.dto.DueOccurrenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
public class GetDueOccurrences {

    private final ListDueOccurrencesAction action;

    public GetDueOccurrences(ListDueOccurrencesAction action) {
        this.action = action;
    }

    @GetMapping("/v1/recurring/due")
    @Operation(summary = "List occurrences waiting for confirmation",
            description = "Every CONFIRM occurrence due up to today that was neither confirmed nor skipped, oldest first.")
    public List<DueOccurrenceResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return action.execute(user);
    }
}
