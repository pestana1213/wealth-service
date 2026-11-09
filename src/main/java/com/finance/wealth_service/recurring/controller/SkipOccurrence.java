package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.SkipOccurrenceAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
public class SkipOccurrence {

    private final SkipOccurrenceAction action;

    public SkipOccurrence(SkipOccurrenceAction action) {
        this.action = action;
    }

    @PostMapping("/v1/recurring/{recurringId}/skip")
    @Operation(summary = "Skip the next occurrence",
            description = "Marks the recurring's next due occurrence as skipped, recording no transaction, and moves the schedule on.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void skip(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID recurringId) {
        action.execute(user, recurringId);
    }
}
