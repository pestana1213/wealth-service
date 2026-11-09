package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.dto.DueOccurrenceResponse;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.repository.RecurringRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class ListDueOccurrencesAction {

    private final RecurringRepository recurringRepository;

    public ListDueOccurrencesAction(RecurringRepository recurringRepository) {
        this.recurringRepository = recurringRepository;
    }

    @Transactional(readOnly = true)
    public List<DueOccurrenceResponse> execute(AuthenticatedUser user) {
        LocalDate today = LocalDate.now();
        List<Recurring> due = recurringRepository.findDueForUser(user.subject(), user.issuer(), today);
        return due.stream()
                .flatMap(recurring -> recurring.occurrencesThrough(today).stream()
                        .map(occurrence -> DueOccurrenceResponse.from(recurring, occurrence)))
                .sorted(Comparator.comparing(DueOccurrenceResponse::dueOn)
                        .thenComparing(DueOccurrenceResponse::name)
                        .thenComparing(DueOccurrenceResponse::recurringId))
                .toList();
    }
}
