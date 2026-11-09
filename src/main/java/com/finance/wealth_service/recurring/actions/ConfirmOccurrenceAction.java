package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.common.Leg;
import com.finance.wealth_service.common.Movement;
import com.finance.wealth_service.common.Occurrence;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.NotFoundException;
import com.finance.wealth_service.exception.WealthException;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.recurring.dto.ConfirmOccurrenceRequest;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.repository.RecurringRepository;
import com.finance.wealth_service.transaction.actions.CreateTransactionAction;
import com.finance.wealth_service.transaction.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ConfirmOccurrenceAction {

    private static final Logger log = LoggerFactory.getLogger(ConfirmOccurrenceAction.class);
    private static final String OPERATION = "confirm";

    private final RecurringRepository recurringRepository;
    private final CreateTransactionAction createTransaction;
    private final TransactionTemplate transactionTemplate;
    private final WealthMetrics metrics;

    public ConfirmOccurrenceAction(RecurringRepository recurringRepository,
                                   CreateTransactionAction createTransaction,
                                   TransactionTemplate transactionTemplate,
                                   WealthMetrics metrics) {
        this.recurringRepository = recurringRepository;
        this.createTransaction = createTransaction;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    public Transaction execute(AuthenticatedUser user, UUID recurringId, ConfirmOccurrenceRequest request) {
        ConfirmOccurrenceRequest body = request == null ? new ConfirmOccurrenceRequest(null, null) : request;
        try {
            Transaction transaction = transactionTemplate.execute(_ -> confirm(user, recurringId, body));
            metrics.recordRecurring(OPERATION, "confirmed");
            log.info("Confirmed occurrence {} of recurring {} as transaction {}",
                    transaction.getOccurrenceOn(), recurringId, transaction.getId());
            return transaction;
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException exception) {
            metrics.recordRecurring(OPERATION, "conflict");
            log.warn("Occurrence of recurring {} was handled concurrently: {}",
                    recurringId, exception.getMostSpecificCause().getMessage());
            throw new ConflictException("This occurrence was already handled; refresh and retry", exception);
        } catch (NotFoundException exception) {
            metrics.recordRecurring(OPERATION, "not_found");
            log.debug("Recurring {} not found for confirm", recurringId);
            throw exception;
        } catch (WealthException exception) {
            metrics.recordRecurring(OPERATION, "rejected");
            log.info("Rejected confirm of recurring {}: {}", recurringId, exception.getMessage());
            throw exception;
        }
    }

    private Transaction confirm(AuthenticatedUser user, UUID recurringId, ConfirmOccurrenceRequest request) {
        Recurring recurring = recurringRepository
                .findRecurringByIdForUser(user.subject(), user.issuer(), recurringId)
                .orElseThrow(() -> new NotFoundException("Recurring not found"));
        LocalDate occurrence = DueOccurrence.of(recurring, LocalDate.now());

        BigDecimal amount = request.transactionAmount() == null
                ? recurring.getTransactionAmount() : request.transactionAmount();
        LocalDate occurredAt = request.occurredAt() == null ? occurrence : request.occurredAt();
        Movement movement = new Movement(user,
                leg(recurring.getProviderAccountId(), recurring.getSourceBucketId()),
                leg(recurring.getDestinationAccountId(), recurring.getDestinationBucketId()),
                amount, recurring.getCategory(), occurredAt, recurring.getName(), null,
                new Occurrence(recurring.getId(), occurrence));

        Transaction transaction = createTransaction.execute(movement);
        recurring.advance();
        recurringRepository.saveAndFlush(recurring);
        return transaction;
    }

    private static Leg leg(UUID accountId, UUID bucketId) {
        return accountId == null ? null : new Leg(accountId, bucketId);
    }
}
