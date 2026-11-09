package com.finance.wealth_service.valuation.actions;

import com.finance.wealth_service.auth.AuthenticatedUser;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.transaction.repository.TransactionRepository;
import com.finance.wealth_service.valuation.dto.CreateValuationRequest;
import com.finance.wealth_service.valuation.entity.AccountValuation;
import com.finance.wealth_service.valuation.repository.AccountValuationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.exception.InvalidRequestException;
import com.finance.wealth_service.exception.NotFoundException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CreateValuationAction {
    private final AccountRepository accounts;
    private final AccountValuationRepository valuations;
    private final TransactionRepository transactions;

    public CreateValuationAction(AccountRepository accounts, AccountValuationRepository valuations,
                                 TransactionRepository transactions) {
        this.accounts = accounts;
        this.valuations = valuations;
        this.transactions = transactions;
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
        // The cost basis is the balance on the valuation date, not today's: a value
        // recorded for March must be compared with what had been put in by March.
        // The account lock above keeps new movements out while this is summed.
        BigDecimal invested = account.getCurrentAmount()
                .subtract(transactions.netInflowAfter(accountId, request.valuedAt()));
        return valuations.saveAndFlush(new AccountValuation(accountId, request.value(), invested, request.valuedAt()));
    }
}
