package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.entity.Transaction;
import com.finance.wealth_service.transaction.entity.TransactionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private final UserRepository users = mock(UserRepository.class);
    private final Ledger ledger = mock(Ledger.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CreateTransactionAction action =
            new CreateTransactionAction(users, ledger, new WealthMetrics(registry));

    @Test
    void mapsConcurrentUpdateToConflict() {
        UUID userId = UUID.randomUUID();
        when(users.existsById(userId)).thenReturn(true);
        when(ledger.post(any())).thenThrow(new OptimisticLockingFailureException("conflict"));
        CreateTransactionRequest request = new CreateTransactionRequest(userId, null, UUID.randomUUID(), null, null,
                null, TransactionType.INCOME, null, null, null);
        assertThatThrownBy(() -> action.execute(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(registry.get("wealth.transactions").tag("outcome", "conflict").counter().count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRouteBeforeStartingTransaction() {
        CreateTransactionRequest request = new CreateTransactionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, TransactionType.INCOME, null, null, null);
        assertThatThrownBy(() -> action.execute(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(ledger);
        assertThat(registry.get("wealth.transactions").tag("outcome", "invalid").counter().count()).isEqualTo(1);
    }

    @Test
    void rejectsTransferWithIdenticalOriginAndDestination() {
        UUID accountId = UUID.randomUUID();
        CreateTransactionRequest request = new CreateTransactionRequest(UUID.randomUUID(), accountId, accountId,
                null, null, null, TransactionType.TRANSFER, null, null, null);
        assertThatThrownBy(() -> action.execute(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(ledger);
        assertThat(registry.get("wealth.transactions").tag("outcome", "invalid").counter().count()).isEqualTo(1);
    }

    @Test
    void recordsCreatedOutcomeAndDurationOnSuccess() {
        UUID userId = UUID.randomUUID();
        when(users.existsById(userId)).thenReturn(true);
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        CreateTransactionRequest request = new CreateTransactionRequest(userId, null, UUID.randomUUID(), null, null,
                BigDecimal.valueOf(500), TransactionType.INCOME, null, LocalDate.now(), "salary");
        assertThat(action.execute(request)).isNotNull();
        assertThat(registry.get("wealth.transactions").tag("outcome", "created").counter().count()).isEqualTo(1);
        assertThat(registry.get("wealth.transaction.duration").tag("outcome", "created").timer().count()).isEqualTo(1);
    }

    @Test
    void carriesTheReversalLinkToTheLedger() {
        UUID userId = UUID.randomUUID();
        UUID original = UUID.randomUUID();
        when(users.existsById(userId)).thenReturn(true);
        when(ledger.post(any())).thenReturn(mock(Transaction.class));
        CreateTransactionRequest request = new CreateTransactionRequest(userId, null, UUID.randomUUID(), null, null,
                BigDecimal.valueOf(10), TransactionType.INCOME, null, LocalDate.now(), null);
        action.execute(new NewTransaction(request, original));
        verify(ledger).post(new NewTransaction(request, original));
    }
}
