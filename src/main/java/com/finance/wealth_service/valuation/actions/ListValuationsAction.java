package com.finance.wealth_service.valuation.actions;

import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.valuation.entity.AccountValuation;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    public List<AccountValuation> execute(UUID accountId) {
        if (!accounts.existsById(accountId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return valuations.findByAccountIdOrderByValuedAtDesc(accountId);
    }
}
