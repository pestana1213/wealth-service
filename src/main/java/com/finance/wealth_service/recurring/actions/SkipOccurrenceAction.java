package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.NotFoundException;
import com.finance.wealth_service.exception.WealthException;
import com.finance.wealth_service.observability.WealthMetrics;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.entity.RecurringSkip;
import com.finance.wealth_service.recurring.repository.RecurringRepository;
import com.finance.wealth_service.recurring.repository.RecurringSkipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class SkipOccurrenceAction {

    private static final Logger log = LoggerFactory.getLogger(SkipOccurrenceAction.class);
    private static final String OPERATION = "skip";

    private final RecurringRepository recurringRepository;
    private final RecurringSkipRepository skipRepository;
    private final TransactionTemplate transactionTemplate;
    private final WealthMetrics metrics;

    public SkipOccurrenceAction(RecurringRepository recurringRepository,
                                RecurringSkipRepository skipRepository,
                                TransactionTemplate transactionTemplate,
                                WealthMetrics metrics) {
        this.recurringRepository = recurringRepository;
        this.skipRepository = skipRepository;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    public void execute(AuthenticatedUser user, UUID recurringId) {
        try {
            LocalDate skipped = transactionTemplate.execute(_ -> skip(user, recurringId));
            metrics.recordRecurring(OPERATION, "skipped");
            log.info("Skipped occurrence {} of recurring {}", skipped, recurringId);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException exception) {
            metrics.recordRecurring(OPERATION, "conflict");
            log.warn("Occurrence of recurring {} was handled concurrently: {}",
                    recurringId, exception.getMostSpecificCause().getMessage());
            throw new ConflictException("This occurrence was already handled; refresh and retry", exception);
        } catch (NotFoundException exception) {
            metrics.recordRecurring(OPERATION, "not_found");
            log.debug("Recurring {} not found for skip", recurringId);
            throw exception;
        } catch (WealthException exception) {
            metrics.recordRecurring(OPERATION, "rejected");
            log.info("Rejected skip of recurring {}: {}", recurringId, exception.getMessage());
            throw exception;
        }
    }

    private LocalDate skip(AuthenticatedUser user, UUID recurringId) {
        Recurring recurring = recurringRepository
                .findRecurringByIdForUser(user.subject(), user.issuer(), recurringId)
                .orElseThrow(() -> new NotFoundException("Recurring not found"));
        LocalDate occurrence = DueOccurrence.of(recurring, LocalDate.now());

        skipRepository.saveAndFlush(new RecurringSkip(recurring.getId(), occurrence));
        recurring.advance();
        recurringRepository.saveAndFlush(recurring);
        return occurrence;
    }
}
