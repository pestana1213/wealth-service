package com.finance.wealth_service.goal.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.goal.actions.CreateGoalAction;
import com.finance.wealth_service.goal.dto.CreateGoalRequest;
import com.finance.wealth_service.goal.dto.CreateGoalResponse;
import com.finance.wealth_service.goal.entity.Goal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Goals", description = "Savings goals")
public class CreateGoal {
    private final CreateGoalAction action;

    public CreateGoal(CreateGoalAction action) {
        this.action = action;
    }

    @PostMapping("/goals")
    @Operation(summary = "Create a goal", description = "Creates a target amount for an existing bucket.")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateGoalResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody CreateGoalRequest request) {
        Goal goal = action.execute(user, request);
        return new CreateGoalResponse(goal.getId(), goal.getBucketId(), goal.getGoalAmount(), goal.getGoalObjective());
    }
}
