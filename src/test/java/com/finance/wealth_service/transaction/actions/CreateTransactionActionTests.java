package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.port.UserPort;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * The guard and the outcome metrics around the ledger. The ledger itself is
 * mocked here: what it does to balances is covered by the integration tests that
 * run against the database.
 */
class CreateTransactionActionTests {
    private final UserPort users = mock(UserPort.class);
    private final Ledger ledger = mock(Ledger.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CreateTransactionAction action =
            new CreateTransactionAction(users, ledger, new WealthMetrics(registry));
    private final AuthenticatedUser caller = new AuthenticatedUser("https://issuer.test", "subject");
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void knownCaller() {
        when(users.requireUserId(caller)).thenReturn(userId);
    }

    @Test
    void mapsConcurrentUpdateToConflict() {
        when(ledger.post(any())).thenThrow(new OptimisticLockingFailureException("conflict"));
        CreateTransactionRequest request = new CreateTransactionRequest(null, UUID.randomUUID(), null, null,
                null, TransactionType.INCOME, null, null, null);
        assertThatThrownBy(() -> action.execute(caller, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(registry.get("wealth.transactions").tag("outcome", "conflict").counter().count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRouteBeforeStartingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest(UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, TransactionType.INCOME, null, null, null);
        assertThatThrownBy(() -> action.execute(caller, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(ledger);
        assertThat(registry.get("wealth.transactions").tag("outcome", "invalid").counter().count()).isEqualTo(1);
    }

    @Test
    void rejectsTransferWithIdenticalOriginAndDestination() {
        UUID accountId = UUID.randomUUID();
        CreateTransactionRequest request = new CreateTransactionRequest(accountId, accountId,
                null, null, null, TransactionType.TRANSFER, null, null, null);
        assertThatThrownBy(() -> action.execute(caller, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(ledger);
        assertThat(registry.get("wealth.transactions").tag("outcome", "invalid").counter().count()).isEqualTo(1);
    }

    @Test
    void recordsCreatedOutcomeAndDurationOnSuccess() {
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        CreateTransactionRequest request = new CreateTransactionRequest(null, UUID.randomUUID(), null, null,
                BigDecimal.valueOf(500), TransactionType.INCOME, null, LocalDate.now(), "salary");
        assertThat(action.execute(caller, request)).isNotNull();
        assertThat(registry.get("wealth.transactions").tag("outcome", "created").counter().count()).isEqualTo(1);
        assertThat(registry.get("wealth.transaction.duration").tag("outcome", "created").timer().count()).isEqualTo(1);
    }

    @Test
    void postsTheMovementAsTheCaller() {
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        CreateTransactionRequest request = new CreateTransactionRequest(null, UUID.randomUUID(), null, null,
                BigDecimal.valueOf(10), TransactionType.INCOME, null, LocalDate.now(), null);
        action.execute(caller, request);
        verify(ledger).post(new NewTransaction(userId, request));
    }

    @Test
    void carriesTheReversalLinkToTheLedger() {
        UUID original = UUID.randomUUID();
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        CreateTransactionRequest request = new CreateTransactionRequest(null, UUID.randomUUID(), null, null,
                BigDecimal.valueOf(10), TransactionType.INCOME, null, LocalDate.now(), null);
        action.execute(new NewTransaction(userId, request, original));
        verify(ledger).post(new NewTransaction(userId, request, original));
    }
}
