package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.NotFoundException;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.repository.RecurringRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class DeleteRecurringAction {

    private static final Logger log = LoggerFactory.getLogger(DeleteRecurringAction.class);
    private static final String OPERATION = "delete";

    private final RecurringRepository recurringRepository;
    private final TransactionTemplate transactionTemplate;
    private final WealthMetrics metrics;

    public DeleteRecurringAction(RecurringRepository recurringRepository,
                                 TransactionTemplate transactionTemplate,
                                 WealthMetrics metrics) {
        this.recurringRepository = recurringRepository;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    public void execute(AuthenticatedUser user, UUID recurringId) {
        try {
            transactionTemplate.executeWithoutResult(_ -> delete(user, recurringId));
            metrics.recordRecurring(OPERATION, "deleted");
            log.info("Deleted recurring {}", recurringId);
        } catch (OptimisticLockingFailureException exception) {
            metrics.recordRecurring(OPERATION, "conflict");
            log.warn("Recurring {} changed while it was being deleted", recurringId);
            throw new ConflictException("This recurring changed while it was being deleted; refresh to see its current state",
                    exception);
        } catch (NotFoundException exception) {
            metrics.recordRecurring(OPERATION, "not_found");
            log.debug("Recurring {} not found for delete", recurringId);
            throw exception;
        }
    }

    private void delete(AuthenticatedUser user, UUID recurringId) {
        Recurring recurring = recurringRepository
                .findRecurringByIdForUser(user.subject(), user.issuer(), recurringId)
                .orElseThrow(() -> new NotFoundException("Recurring not found"));
        recurringRepository.delete(recurring);
        recurringRepository.flush();
    }
}
