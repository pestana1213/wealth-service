package com.finance.wealth_service.goal.controller;

import com.finance.wealth_service.goal.actions.UpdateGoalAction;
import com.finance.wealth_service.goal.dto.CreateGoalResponse;
import com.finance.wealth_service.goal.dto.UpdateGoalRequest;
import com.finance.wealth_service.goal.entity.Goal;
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
            description = "Updates the target amount and/or date. Omitted fields are left unchanged; the bucket is fixed.")
    public CreateGoalResponse updateGoal(@PathVariable UUID goalId, @Valid @RequestBody UpdateGoalRequest request) {
        Goal goal = action.execute(goalId, request);
        return new CreateGoalResponse(goal.getId(), goal.getBucketId(), goal.getGoalAmount(), goal.getGoalObjective());
    }
}
