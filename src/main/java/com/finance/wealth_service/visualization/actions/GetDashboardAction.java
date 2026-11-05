package com.finance.wealth_service.visualization.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import java.util.Optional;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.entity.Goal;
import com.finance.wealth_service.goal.repository.GoalRepository;
import com.finance.wealth_service.account.port.UserPort;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.valuation.entity.AccountValuation;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
import com.finance.wealth_service.visualization.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GetDashboardAction {
    private final UserPort users;
    private final AccountRepository accounts;
    private final BucketRepository buckets;
    private final GoalRepository goals;
    private final AccountValuationRepository accountValuations;
    private final WealthMetrics metrics;

    public GetDashboardAction(UserPort users, AccountRepository accounts, BucketRepository buckets,
                              GoalRepository goals, WealthMetrics metrics, AccountValuationRepository accountValuations) {
        this.users = users; this.accounts = accounts; this.buckets = buckets; this.goals = goals; this.metrics = metrics;
        this.accountValuations = accountValuations;
    }

    private Map<UUID, AccountValuation> latestValuations(List<Account> userAccounts) {
        if (userAccounts.isEmpty()) {
            return Map.of();
        }
        return accountValuations.findLatestPerAccount(userAccounts.stream().map(Account::getId).toList())
                .stream().collect(Collectors.toMap(AccountValuation::getAccountId, valuation -> valuation));
    }

    private BigDecimal investedOf(AccountValuation valuation, Account account) {
        return valuation.getInvestedMoney() == null ? account.getCurrentAmount() : valuation.getInvestedMoney();
    }

    public DashboardResponse execute(AuthenticatedUser user) {
        long started = System.nanoTime();
        UUID userId = users.findUserId(user).orElseThrow(() -> {
            metrics.recordDashboardFailure("not_found");
            metrics.recordDashboard("not_found", System.nanoTime() - started);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        });
        try {
            DashboardResponse response = summarise(userId, accountViews(userId));
            metrics.recordDashboard("ok", System.nanoTime() - started);
            return response;
        } catch (RuntimeException exception) {
            metrics.recordDashboardFailure("error");
            metrics.recordDashboard("error", System.nanoTime() - started);
            throw exception;
        }
    }

    private static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> amount) {
        return items.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static GoalView goalView(Goal goal) {
        return new GoalView(goal.getId(), goal.getGoalAmount(), goal.getGoalObjective());
    }

    private AccountView accountView(Account account, AccountValuation valuation,
                                    List<Bucket> accountBuckets, Map<UUID, List<Goal>> goalsByBucket) {
        List<BucketView> bucketViews = accountBuckets.stream()
                .map(b -> new BucketView(b.getId(), b.getName(), b.getCurrentAmount(),
                        goalsByBucket.getOrDefault(b.getId(), List.of()).stream()
                                .map(GetDashboardAction::goalView).toList()))
                .toList();
        BigDecimal allocated = sum(bucketViews, BucketView::currentAmount);
        return new AccountView(account.getId(), account.getName(), account.getCurrentAmount(),
                allocated, account.getCurrentAmount().subtract(allocated),
                valuation == null ? null : valuation.getValue(),
                valuation == null ? null : investedOf(valuation, account),
                bucketViews);
    }

    public List<AccountView> accountViews(UUID userId) {
        List<Account> userAccounts = accounts.findByUserIdOrderByName(userId);
        Map<UUID, AccountValuation> latest = latestValuations(userAccounts);

        List<Bucket> activeBuckets = buckets.findByUserIdAndArchivedFalseOrderByName(userId);
        Map<UUID, List<Bucket>> bucketsByAccount = activeBuckets.stream()
                .collect(Collectors.groupingBy(Bucket::getAccountId));
        Map<UUID, List<Goal>> goalsByBucket = goals.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(Goal::getBucketId));

        return userAccounts.stream().map(a -> accountView(a, latest.get(a.getId()),
                bucketsByAccount.getOrDefault(a.getId(), List.of()), goalsByBucket)).toList();
    }

    public List<AccountView> accounts(AuthenticatedUser user) {
        UUID userId = users.findUserId(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return accountViews(userId);
    }

    private static DashboardResponse summarise(UUID userId, List<AccountView> accounts) {
        BigDecimal total = sum(accounts, AccountView::currentAmount);
        BigDecimal allocated = sum(accounts, AccountView::allocatedAmount);
        BigDecimal market = sum(accounts, a -> a.marketValue() == null ? a.currentAmount() : a.marketValue());
        BigDecimal gain = sum(accounts.stream().filter(a -> a.marketValue() != null).toList(),
                a -> a.marketValue().subtract(a.investedMoney()));
        return new DashboardResponse(userId, total, allocated, total.subtract(allocated), market, gain, accounts);
    }

}
