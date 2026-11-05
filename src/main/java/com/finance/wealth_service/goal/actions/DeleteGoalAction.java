package com.finance.wealth_service.goal.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.goal.entity.Goal;

import com.finance.wealth_service.goal.repository.GoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DeleteGoalAction {
    private final GoalRepository goals;

    public DeleteGoalAction(GoalRepository goals) {
        this.goals = goals;
    }

    /** Nothing references a goal and it holds no money, so the row is removed outright. */
    @Transactional
    public void execute(AuthenticatedUser user, UUID goalId) {
        Goal goal = goals.findOwnedBy(goalId, user.subject(), user.issuer())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        goals.delete(goal);
    }
}
