package com.finance.wealth_service.visualization.actions;

import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.goal.repository.GoalRepository;
import com.finance.wealth_service.account.repository.UserRepository;
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
import java.util.stream.Collectors;

@Service
public class GetDashboardAction {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final BucketRepository buckets;
    private final GoalRepository goals;
    private final AccountValuationRepository accountValuations;
    private final WealthMetrics metrics;

    public GetDashboardAction(UserRepository users, AccountRepository accounts, BucketRepository buckets,
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

    public DashboardResponse execute(UUID userId) {
        long started = System.nanoTime();
        if (!users.existsById(userId)) {
            metrics.recordDashboardFailure("not_found");
            metrics.recordDashboard("not_found", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        try {
            List<Account> userAccounts = accounts.findByUserIdOrderByName(userId);
            Map<UUID, AccountValuation> latest = latestValuations(userAccounts);
            List<AccountView> accountViews = userAccounts.stream().map(account -> {
                AccountValuation valuation = latest.get(account.getId());
                return new AccountView(account.getId(), account.getName(), account.getCurrentAmount(),
                        valuation == null ? null : valuation.getValue(),
                        valuation == null ? null : investedOf(valuation, account));
            }).toList();
            List<Bucket> activeBuckets = buckets.findByUserIdAndArchivedFalseOrderByName(userId);
            List<BucketView> bucketViews = activeBuckets.stream()
                    .map(b -> new BucketView(b.getId(), b.getName(), b.getCurrentAmount())).toList();
            Map<UUID, BigDecimal> amounts = activeBuckets.stream().collect(Collectors.toMap(Bucket::getId, Bucket::getCurrentAmount));
            List<GoalView> goalViews = goals.findByUserId(userId).stream()
                    .map(g -> new GoalView(g.getId(), g.getBucketId(), g.getGoalAmount(), g.getGoalObjective(), amounts.getOrDefault(g.getBucketId(), BigDecimal.ZERO))).toList();
            BigDecimal total = accountViews.stream().map(AccountView::currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal allocated = bucketViews.stream().map(BucketView::currentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            // An account with no valuation is worth what it holds.
            BigDecimal marketAmount = accountViews.stream()
                    .map(view -> view.marketValue() == null ? view.currentAmount() : view.marketValue())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // Only a valued account can show a gain; the others have nothing to compare to.
            BigDecimal marketGain = accountViews.stream()
                    .filter(view -> view.marketValue() != null)
                    .map(view -> view.marketValue().subtract(view.investedMoney()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            metrics.recordDashboard("ok", System.nanoTime() - started);
            return new DashboardResponse(userId, total, allocated, total.subtract(allocated),
                    marketAmount, marketGain, accountViews, bucketViews, goalViews);
        } catch (RuntimeException exception) {
            metrics.recordDashboardFailure("error");
            metrics.recordDashboard("error", System.nanoTime() - started);
            throw exception;
        }
    }
}
