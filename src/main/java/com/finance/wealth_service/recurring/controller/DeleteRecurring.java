package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.DeleteRecurringAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
public class DeleteRecurring {

    private final DeleteRecurringAction action;

    public DeleteRecurring(DeleteRecurringAction action) {
        this.action = action;
    }

    @DeleteMapping("/v1/recurring/{recurringId}")
    @Operation(summary = "Delete a recurring",
            description = "Removes the recurring and its skipped occurrences. Transactions it already recorded are kept.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID recurringId) {
        action.execute(user, recurringId);
    }
}
