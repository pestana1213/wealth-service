package com.finance.wealth_service.recurring.controller;

import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.actions.CreateRecurringAction;
import com.finance.wealth_service.recurring.dto.CreateRecurringRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Recurring", description = "Recurring movements")
@RequestMapping("v1/recurring")
public class CreateRecurring {

    private final CreateRecurringAction createRecurringAction;

    public CreateRecurring(CreateRecurringAction createRecurringAction) {
        this.createRecurringAction = createRecurringAction;
    }

    @PostMapping
    public void createRecurring(@AuthenticationPrincipal AuthenticatedUser user,
                                @RequestBody CreateRecurringRequest request) {
        createRecurringAction.execute(user, request);
    }

}
