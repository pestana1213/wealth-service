package com.finance.wealth_service.valuation.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.valuation.dto.CreateValuationRequest;
import com.finance.wealth_service.valuation.entity.AccountValuation;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.InvalidRequestException;
import com.finance.wealth_service.exception.NotFoundException;

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
    public AccountValuation execute(AuthenticatedUser user, UUID accountId, CreateValuationRequest request) {
        Account account = accounts.findOwnedByForUpdate(accountId, user.subject(), user.issuer())
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (account.getType() != AccountType.INVESTMENT) {
            throw new InvalidRequestException("Valuations can only be recorded for investment accounts");
        }
        if (valuations.existsByAccountIdAndValuedAt(accountId, request.valuedAt())) {
            throw new ConflictException("A valuation is already recorded for this date");
        }
        return valuations.saveAndFlush(new AccountValuation(accountId, request.value(),
                account.getCurrentAmount(),request.valuedAt()));
    }
}
