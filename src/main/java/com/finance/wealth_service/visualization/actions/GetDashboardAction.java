package com.finance.wealth_service.visualization.actions;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.repository.GoalRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.visualization.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetDashboardAction {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final BucketRepository buckets;
    private final GoalRepository goals;
    private final WealthMetrics metrics;

    public GetDashboardAction(UserRepository users, AccountRepository accounts, BucketRepository buckets, GoalRepository goals, WealthMetrics metrics) {
        this.users = users; this.accounts = accounts; this.buckets = buckets; this.goals = goals; this.metrics = metrics;
    }

    public DashboardResponse execute(UUID userId) {
        long started = System.nanoTime();
        if (!users.existsById(userId)) {
            metrics.recordDashboardFailure("not_found");
            metrics.recordDashboard("not_found", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        try {
            List<AccountView> accountViews = accounts.findByUserIdOrderByName(userId).stream()
                    .map(a -> new AccountView(a.getId(), a.getName(), a.getCurrentAmount())).toList();
            List<Bucket> activeBuckets = buckets.findByUserIdAndArchivedFalseOrderByName(userId);
            List<BucketView> bucketViews = activeBuckets.stream()
                    .map(b -> new BucketView(b.getId(), b.getName(), b.getCurrentAmount())).toList();
            Map<UUID, BigDecimal> amounts = activeBuckets.stream().collect(Collectors.toMap(Bucket::getId, Bucket::getCurrentAmount));
            List<GoalView> goalViews = goals.findByUserId(userId).stream()
                    .map(g -> new GoalView(g.getId(), g.getBucketId(), g.getGoalAmount(), g.getGoalObjective(), amounts.getOrDefault(g.getBucketId(), BigDecimal.ZERO))).toList();
            BigDecimal total = accountViews.stream().map(AccountView::currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal allocated = bucketViews.stream().map(BucketView::currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            metrics.recordDashboard("ok", System.nanoTime() - started);
            return new DashboardResponse(userId, total, allocated, total.subtract(allocated), accountViews, bucketViews, goalViews);
        } catch (RuntimeException exception) {
            metrics.recordDashboardFailure("error");
            metrics.recordDashboard("error", System.nanoTime() - started);
            throw exception;
        }
    }
}
