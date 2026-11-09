package com.finance.wealth_service.valuation.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.exception.InvalidRequestException;
import com.finance.wealth_service.valuation.entity.AccountValuation;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finance.wealth_service.exception.NotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ListValuationsAction {
    private final AccountRepository accounts;
    private final AccountValuationRepository valuations;

    public ListValuationsAction(AccountRepository accounts, AccountValuationRepository valuations) {
        this.accounts = accounts;
        this.valuations = valuations;
    }

    @Transactional(readOnly = true)
    public List<AccountValuation> execute(AuthenticatedUser user,
                                          UUID accountId,
                                          LocalDate startDate,
                                          LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new InvalidRequestException("'to' must not be before 'from'");
        }

        if (accounts.findOwnedBy(accountId, user.subject(), user.issuer()).isEmpty()) {
            throw new NotFoundException("Account not found");
        }
        return valuations.findForAccountBetween(accountId, startDate, endDate);
    }
}
