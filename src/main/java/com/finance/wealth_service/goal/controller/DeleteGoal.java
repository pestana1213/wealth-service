package com.finance.wealth_service.goal.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.goal.actions.DeleteGoalAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Goals", description = "Target amounts for buckets")
public class DeleteGoal {
    private final DeleteGoalAction action;

    public DeleteGoal(DeleteGoalAction action) {
        this.action = action;
    }

    @DeleteMapping("/goals/{goalId}")
    @Operation(summary = "Delete a goal", description = "Removes the goal. The bucket and its allocation are untouched.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGoal(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID goalId) {
        action.execute(user, goalId);
    }
}
