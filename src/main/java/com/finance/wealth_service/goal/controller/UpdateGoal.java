package com.finance.wealth_service.goal.controller;

import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.finance.wealth_service.goal.actions.UpdateGoalAction;
import com.finance.wealth_service.goal.dto.CreateGoalResponse;
import com.finance.wealth_service.goal.dto.UpdateGoalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Goals", description = "Target amounts for buckets")
public class UpdateGoal {
    private final UpdateGoalAction action;

    public UpdateGoal(UpdateGoalAction action) {
        this.action = action;
    }

    @PatchMapping("/goals/{goalId}")
    @Operation(summary = "Update a goal",
            description = "Updates the target amount and/or date. Omitted fields are left unchanged; "
                    + "goalObjective sent as null removes the date. The bucket is fixed.")
    public CreateGoalResponse updateGoal(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID goalId,
                                         @Valid @RequestBody UpdateGoalRequest request) {
        return CreateGoalResponse.from(action.execute(user, goalId, request));
    }
}
