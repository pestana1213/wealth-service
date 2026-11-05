package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.bucket.entity.Bucket;
import com.finance.wealth_service.bucket.port.BucketPort;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.dto.Leg;
import com.finance.wealth_service.transaction.dto.Movement;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * The guard, the resolution of buckets to their accounts and the outcome metrics
 * around the ledger. The ledger itself is mocked here: what it does to balances
 * is covered by the integration tests that run against the database.
 */
class CreateTransactionActionTests {
    private final Ledger ledger = mock(Ledger.class);
    private final BucketPort bucketPort = mock(BucketPort.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CreateTransactionAction action =
            new CreateTransactionAction(ledger, new WealthMetrics(registry), bucketPort);
    private final AuthenticatedUser caller = new AuthenticatedUser("https://issuer.test", "subject");
    private final LocalDate today = LocalDate.now();

    private static CreateTransactionRequest request(UUID fromAccount, UUID fromBucket, UUID toAccount, UUID toBucket) {
        return new CreateTransactionRequest(fromAccount, fromBucket, toAccount, toBucket,
                BigDecimal.TEN, TransactionCategory.OTHER, LocalDate.now(), null);
    }

    private void assertRejectedAsInvalid(CreateTransactionRequest request) {
        assertThatThrownBy(() -> action.execute(caller, request)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(ledger);
        assertThat(registry.get("wealth.transactions").tag("outcome", "invalid").counter().count()).isEqualTo(1);
    }

    @Test
    void mapsConcurrentUpdateToConflict() {
        when(ledger.post(any())).thenThrow(new OptimisticLockingFailureException("conflict"));
        assertThatThrownBy(() -> action.execute(caller, request(null, null, UUID.randomUUID(), null)))
                .isInstanceOf(ConflictException.class);
        assertThat(registry.get("wealth.transactions").tag("outcome", "conflict").counter().count()).isEqualTo(1);
    }

    @Test
    void rejectsMovementWithoutOriginOrDestination() {
        assertRejectedAsInvalid(request(null, null, null, null));
    }

    @Test
    void rejectsAccountAndBucketOnTheSameSide() {
        assertRejectedAsInvalid(request(UUID.randomUUID(), UUID.randomUUID(), null, null));
        verifyNoInteractions(bucketPort);
    }

    @Test
    void rejectsIdenticalOriginAndDestination() {
        UUID accountId = UUID.randomUUID();
        assertRejectedAsInvalid(request(accountId, null, accountId, null));
    }

    /** Resolution runs inside the metered attempt, so an unknown bucket still counts as invalid. */
    @Test
    void rejectsUnknownBucket() {
        when(bucketPort.findOwnedBy(any(), any(), any())).thenReturn(Optional.empty());
        assertRejectedAsInvalid(request(null, UUID.randomUUID(), null, null));
    }

    @Test
    void recordsCreatedOutcomeAndDurationOnSuccess() {
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        assertThat(action.execute(caller, request(null, null, UUID.randomUUID(), null))).isNotNull();
        assertThat(registry.get("wealth.transactions").tag("outcome", "created").counter().count()).isEqualTo(1);
        assertThat(registry.get("wealth.transaction.duration").tag("outcome", "created").timer().count()).isEqualTo(1);
    }

    @Test
    void postsTheMovementAsTheCaller() {
        UUID accountId = UUID.randomUUID();
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        action.execute(caller, request(null, null, accountId, null));
        verify(ledger).post(new Movement(caller, null, new Leg(accountId, null),
                BigDecimal.TEN, TransactionCategory.OTHER, today, null, null));
    }

    @Test
    void resolvesABucketToTheAccountItLivesIn() {
        UUID accountId = UUID.randomUUID();
        UUID bucketId = UUID.randomUUID();
        when(bucketPort.findOwnedBy(bucketId, caller.subject(), caller.issuer()))
                .thenReturn(Optional.of(new Bucket(UUID.randomUUID(), accountId, "House")));
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        action.execute(caller, request(null, bucketId, null, null));
        verify(ledger).post(new Movement(caller, new Leg(accountId, bucketId), null,
                BigDecimal.TEN, TransactionCategory.OTHER, today, null, null));
    }

    @Test
    void carriesTheReversalLinkToTheLedger() {
        UUID original = UUID.randomUUID();
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        Movement reversal = new Movement(caller, null, new Leg(UUID.randomUUID(), null),
                BigDecimal.TEN, TransactionCategory.OTHER, today, null, original);
        action.execute(reversal);
        verify(ledger).post(reversal);
    }
}
