package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.bucket.repository.BucketRepository;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.entity.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CreateTransactionActionTests {
    private final UserRepository users = mock(UserRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final BucketRepository buckets = mock(BucketRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionTemplate template = mock(TransactionTemplate.class);
    private final CreateTransactionAction action = new CreateTransactionAction(users, accounts, buckets, template, transactions);

    @Test
    void mapsConcurrentUpdateToConflict() {
        UUID userId = UUID.randomUUID();
        when(users.existsById(userId)).thenReturn(true);
        when(template.execute(any())).thenThrow(new OptimisticLockingFailureException("conflict"));
        var request = new CreateTransactionRequest(userId, null, UUID.randomUUID(), null, null,
                null, TransactionType.INCOME, null, null, null);
        assertThatThrownBy(() -> action.execute(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsInvalidRouteBeforeStartingTransaction() {
        var request = new CreateTransactionRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, TransactionType.INCOME, null, null, null);
        assertThatThrownBy(() -> action.execute(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(template, accounts, buckets, transactions);
    }

    @Test
    void rejectsTransferWithIdenticalOriginAndDestination() {
        UUID accountId = UUID.randomUUID();
        var request = new CreateTransactionRequest(UUID.randomUUID(), accountId, accountId,
                null, null, null, TransactionType.TRANSFER, null, null, null);
        assertThatThrownBy(() -> action.execute(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(template, accounts, buckets, transactions);
    }
}
