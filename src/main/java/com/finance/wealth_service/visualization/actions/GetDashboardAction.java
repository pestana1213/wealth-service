package com.finance.wealth_service.visualization.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

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
import org.springframework.stereotype.Service;
import com.finance.wealth_service.exception.NotFoundException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the dashboard from several queries: the user, the accounts, their latest
 * valuations, the active buckets and their goals.
 *
 * <p><b>Why a transaction, and why {@code REPEATABLE_READ}.</b> The numbers only
 * add up if every query sees the same moment: an account's available amount is
 * its balance minus the buckets that live in it, and those come from different
 * queries. PostgreSQL reads from a snapshot of committed data, and the isolation
 * level decides when that snapshot is taken:
 * <ul>
 *   <li>{@code READ COMMITTED}, the default, takes a new snapshot for every
 *       statement. A transfer that commits between the account query and the
 *       bucket query would be half visible: the bucket already credited, the
 *       account not yet debited, and the available amount wrong for that request.</li>
 *   <li>{@code REPEATABLE_READ} takes one snapshot at the first statement and keeps
 *       it for the whole transaction, so all the queries see the same state: either
 *       before the transfer or after it, never in between.</li>
 * </ul>
 * In PostgreSQL this costs next to nothing for a reader: MVCC takes no locks, so
 * writers never wait for the dashboard, and a transaction that only reads can never
 * hit the serialization failures {@code REPEATABLE_READ} raises for writers.
 *
 * <p>{@code readOnly} lets Hibernate skip flushing and dirty checking, and makes the
 * driver open the transaction as {@code READ ONLY}, so an accidental write fails
 * instead of slipping through.
 *
 * <p>The annotation sits on the public entry points the controller calls. A call
 * from inside this class goes through {@code this}, not the Spring proxy, so
 * annotating only {@link #accountViews} would be ignored when {@link #execute}
 * calls it.
 */
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

    /** One snapshot for every query behind the dashboard; see the class comment. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse execute(AuthenticatedUser user) {
        long started = System.nanoTime();
        UUID userId = users.findUserId(user).orElseThrow(() -> {
            metrics.recordDashboardFailure("not_found");
            metrics.recordDashboard("not_found", System.nanoTime() - started);
            return new NotFoundException("User not found");
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

    /** The caller's accounts on their own, outside the dashboard metrics; same snapshot rule as {@link #execute}. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AccountView> accounts(AuthenticatedUser user) {
        UUID userId = users.findUserId(user)
                .orElseThrow(() -> new NotFoundException("User not found"));
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
