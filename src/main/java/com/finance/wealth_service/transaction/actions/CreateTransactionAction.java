package com.finance.wealth_service.transaction.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.transaction.dto.CreateTransactionRequest;
import com.finance.wealth_service.transaction.entity.Transaction;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Guards the request, hands the movement to the {@link Ledger} and turns what
 * comes back into an HTTP outcome. The database transaction lives in the ledger,
 * so the translation below runs after it has already rolled back.
 */
@Service
public class CreateTransactionAction {
    private final Ledger ledger;
    private final WealthMetrics metrics;

    public CreateTransactionAction(Ledger ledger, WealthMetrics metrics) {
        this.ledger = ledger;
        this.metrics = metrics;
    }

    public Transaction execute(AuthenticatedUser user, CreateTransactionRequest request) {
        return execute(new NewTransaction(user, request));
    }

    public Transaction execute(NewTransaction movement) {
        long started = System.nanoTime();
        if (!validate(movement.request())) {
            metrics.recordTransaction("invalid", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transaction origin, destination or ownership");
        }

        try {
            Transaction created = ledger.post(movement);
            metrics.recordTransaction("created", System.nanoTime() - started);
            return created;
        } catch (PessimisticLockingFailureException exception) {
            metrics.recordTransaction("conflict", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Account or bucket is busy; retry the transaction", exception);
        } catch (OptimisticLockingFailureException exception) {
            metrics.recordTransaction("conflict", System.nanoTime() - started);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Balance changed concurrently; retry the transaction", exception);
        } catch (ResponseStatusException exception) {
            metrics.recordTransaction("invalid", System.nanoTime() - started);
            throw exception;
        }
    }

    private boolean validate(CreateTransactionRequest request) {
        if (request == null || request.type() == null) {
            return false;
        }

        boolean validRoute = switch (request.type()) {
            case INCOME -> request.providerAccountId() == null
                    && request.sourceBucketId() == null
                    && request.destinationAccountId() != null;
            case EXPENSE -> request.providerAccountId() != null
                    && request.destinationAccountId() == null
                    && request.destinationBucketId() == null;
            case TRANSFER -> request.providerAccountId() != null
                    && request.destinationAccountId() != null
                    && (!request.providerAccountId().equals(request.destinationAccountId())
                        || !Objects.equals(request.sourceBucketId(), request.destinationBucketId()));
        };

        return validRoute;
    }
}
