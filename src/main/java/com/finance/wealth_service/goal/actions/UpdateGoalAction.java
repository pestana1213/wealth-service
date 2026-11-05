package com.finance.wealth_service.goal.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.goal.dto.UpdateGoalRequest;
import com.finance.wealth_service.goal.entity.Goal;
import com.finance.wealth_service.goal.repository.GoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UpdateGoalAction {
    private final GoalRepository goals;

    public UpdateGoalAction(GoalRepository goals) {
        this.goals = goals;
    }

    @Transactional
    public Goal execute(AuthenticatedUser user, UUID goalId, UpdateGoalRequest request) {
        Goal goal = goals.findOwnedBy(goalId, user.subject(), user.issuer())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        if (request.goalAmount() != null) {
            goal.setGoalAmount(request.goalAmount());
        }
        if (request.goalObjective() != null) {
            goal.setGoalObjective(request.goalObjective());
        }
        return goals.saveAndFlush(goal);
    }
}
