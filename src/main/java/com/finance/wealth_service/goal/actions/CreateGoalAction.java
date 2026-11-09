package com.finance.wealth_service.goal.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.dto.CreateGoalRequest;
import com.finance.wealth_service.goal.entity.Goal;
import com.finance.wealth_service.goal.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.NotFoundException;

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
        Bucket bucket = buckets.findOwnedBy(request.bucketId(), user.subject(), user.issuer())
                .orElseThrow(() -> new NotFoundException("Bucket not found"));
        // findOwnedBy includes archived buckets; a goal on one would be stored but never shown.
        if (bucket.isArchived()) {
            throw new ConflictException("Bucket is archived");
        }
        return goals.save(new Goal(request.bucketId(), request.goalAmount(), request.goalObjective()));
    }
}
