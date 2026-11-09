package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.ConfirmOccurrenceAction;
import com.finance.wealth_service.recurring.dto.ConfirmOccurrenceRequest;
import com.finance.wealth_service.transaction.dto.CreateTransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
public class ConfirmOccurrence {

    private final ConfirmOccurrenceAction action;

    public ConfirmOccurrence(ConfirmOccurrenceAction action) {
        this.action = action;
    }

    @PostMapping("/v1/recurring/{recurringId}/confirm")
    @Operation(summary = "Confirm the next occurrence",
            description = "Records the recurring's next due occurrence as a transaction and moves the schedule on.")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateTransactionResponse confirm(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID recurringId,
                                             @Valid @RequestBody(required = false) ConfirmOccurrenceRequest request) {
        return CreateTransactionResponse.from(action.execute(user, recurringId, request));
    }
}
