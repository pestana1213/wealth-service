package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.CreateRecurringAction;
import com.finance.wealth_service.recurring.dto.CreateRecurringRequest;
import com.finance.wealth_service.recurring.dto.RecurringResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
public class CreateRecurring {

    private final CreateRecurringAction createRecurringAction;

    public CreateRecurring(CreateRecurringAction createRecurringAction) {
        this.createRecurringAction = createRecurringAction;
    }

    @PostMapping("/v1/recurring")
    @Operation(summary = "Create a recurring", description = "Creates a movement that repeats on a schedule.")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringResponse createRecurring(@AuthenticationPrincipal AuthenticatedUser user,
                                             @Valid @RequestBody CreateRecurringRequest request) {
        return RecurringResponse.from(createRecurringAction.execute(user, request));
    }
}
