package com.finance.wealth_service.goal.actions;

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
    public void execute(UUID goalId) {
        if (!goals.existsById(goalId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found");
        }
        goals.deleteById(goalId);
    }
}
