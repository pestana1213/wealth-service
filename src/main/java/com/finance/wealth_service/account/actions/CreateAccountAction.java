package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.dto.CreateAccountRequest;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.port.UserPort;
import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CreateAccountAction {
    private final AccountRepository accounts;
    private final UserPort users;

    public CreateAccountAction(AccountRepository accounts, UserPort users) {
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional
    public Account execute(AuthenticatedUser user, CreateAccountRequest request) {
        UUID userId = users.requireUserId(user);
        BigDecimal amount = request.currentAmount() == null ? BigDecimal.ZERO : request.currentAmount();
        AccountType type = request.type() == null ? AccountType.SAVINGS : request.type();
        return accounts.save(new Account(userId, request.name().strip(), amount, type));
    }
}
