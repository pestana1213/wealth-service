package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.repository.RecurringRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListRecurringAction {

    private final RecurringRepository recurringRepository;

    public ListRecurringAction(RecurringRepository recurringRepository) {
        this.recurringRepository = recurringRepository;
    }

    @Transactional(readOnly = true)
    public List<Recurring> execute(AuthenticatedUser user) {
        return recurringRepository.findAllForUser(user.subject(), user.issuer());
    }
}
