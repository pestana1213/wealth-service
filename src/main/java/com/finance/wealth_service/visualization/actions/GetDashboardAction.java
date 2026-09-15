package com.finance.wealth_service.visualization.actions;

import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.repository.GoalRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.visualization.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetDashboardAction {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final BucketRepository buckets;
    private final GoalRepository goals;

    public GetDashboardAction(UserRepository users, AccountRepository accounts, BucketRepository buckets, GoalRepository goals) {
        this.users = users; this.accounts = accounts; this.buckets = buckets; this.goals = goals;
    }

    public DashboardResponse execute(UUID userId) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        var accountViews = accounts.findByUserIdOrderByName(userId).stream()
                .map(a -> new AccountView(a.getId(), a.getName(), a.getCurrentAmount())).toList();
        var bucketViews = buckets.findByUserIdOrderByName(userId).stream()
                .map(b -> new BucketView(b.getId(), b.getName(), b.getCurrentAmount())).toList();
        var amounts = buckets.findByUserIdOrderByName(userId).stream().collect(Collectors.toMap(Bucket::getId, Bucket::getCurrentAmount));
        var goalViews = goals.findByUserId(userId).stream()
                .map(g -> new GoalView(g.getId(), g.getBucketId(), g.getGoalAmount(), g.getGoalObjective(), amounts.getOrDefault(g.getBucketId(), BigDecimal.ZERO))).toList();
        BigDecimal total = accountViews.stream().map(AccountView::currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = bucketViews.stream().map(BucketView::currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardResponse(userId, total, allocated, total.subtract(allocated), accountViews, bucketViews, goalViews);
    }
}
