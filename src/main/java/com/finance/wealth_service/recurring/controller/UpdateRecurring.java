package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.UpdateRecurringAction;
import com.finance.wealth_service.recurring.dto.RecurringResponse;
import com.finance.wealth_service.recurring.dto.UpdateRecurringRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
public class UpdateRecurring {

    private final UpdateRecurringAction action;

    public UpdateRecurring(UpdateRecurringAction action) {
        this.action = action;
    }

    @PatchMapping("/v1/recurring/{recurringId}")
    @Operation(summary = "Update a recurring",
            description = "Omitted fields are left unchanged. Accounts, buckets and endsOn sent as null are removed. "
                    + "Changing the schedule recomputes nextOn from today, dropping occurrences still pending before it.")
    public RecurringResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable UUID recurringId,
                                    @Valid @RequestBody UpdateRecurringRequest request) {
        return RecurringResponse.from(action.execute(user, recurringId, request));
    }
}
