package com.finance.wealth_service.valuation.actions;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.valuation.dto.CreateValuationRequest;
import com.finance.wealth_service.valuation.entity.AccountValuation;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CreateValuationAction {
    private final AccountRepository accounts;
    private final AccountValuationRepository valuations;

    public CreateValuationAction(AccountRepository accounts, AccountValuationRepository valuations) {
        this.accounts = accounts;
        this.valuations = valuations;
    }

    @Transactional
    public AccountValuation execute(UUID accountId, CreateValuationRequest request) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getType() != AccountType.INVESTMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valuations can only be recorded for investment accounts");
        }
        if (valuations.existsByAccountIdAndValuedAt(accountId, request.valuedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A valuation is already recorded for this date");
        }
        return valuations.saveAndFlush(new AccountValuation(accountId, request.value(), request.valuedAt()));
    }
}
