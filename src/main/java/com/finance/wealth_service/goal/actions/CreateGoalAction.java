package com.finance.wealth_service.goal.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.dto.CreateGoalRequest;
import com.finance.wealth_service.goal.entity.Goal;
import com.finance.wealth_service.goal.repository.GoalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.RoundingMode;

@Service
public class CreateGoalAction {
    private final GoalRepository goals;
    private final BucketRepository buckets;

    public CreateGoalAction(GoalRepository goals, BucketRepository buckets) {
        this.goals = goals;
        this.buckets = buckets;
    }

    @Transactional
    public Goal execute(AuthenticatedUser user, CreateGoalRequest request) {
        if (buckets.findOwnedBy(request.bucketId(), user.subject(), user.issuer()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bucket not found");
        }
        return goals.save(new Goal(request.bucketId(),
                request.goalAmount().setScale(2, RoundingMode.UNNECESSARY), request.goalObjective()));
    }
}
