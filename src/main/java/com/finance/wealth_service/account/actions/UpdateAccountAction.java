package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.dto.UpdateAccountRequest;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UpdateAccountAction {
    private final AccountRepository accounts;

    public UpdateAccountAction(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account execute(UUID accountId, UpdateAccountRequest request) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (request.name() != null) {
            account.setName(request.name().strip());
        }
        if (request.type() != null) {
            account.setType(request.type());
        }
        return accounts.saveAndFlush(account);
    }
}
