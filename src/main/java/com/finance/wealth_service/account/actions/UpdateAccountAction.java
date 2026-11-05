package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.dto.UpdateAccountRequest;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finance.wealth_service.exception.NotFoundException;

@Service
public class UpdateAccountAction {
    private final AccountRepository accounts;

    public UpdateAccountAction(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account execute(AuthenticatedUser user, UpdateAccountRequest request) {
        Account account = accounts.findOwnedBy(request.accountId() ,user.subject(), user.issuer())
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (request.name() != null) {
            account.setName(request.name().strip());
        }
        if (request.type() != null) {
            account.setType(request.type());
        }
        return accounts.saveAndFlush(account);
    }
}
