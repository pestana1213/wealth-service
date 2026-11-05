package com.finance.wealth_service.goal.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.goal.entity.Goal;

import com.finance.wealth_service.goal.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finance.wealth_service.exception.NotFoundException;

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
                .orElseThrow(() -> new NotFoundException("Goal not found"));
        goals.delete(goal);
    }
}
