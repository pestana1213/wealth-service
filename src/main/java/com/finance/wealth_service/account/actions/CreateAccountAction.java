package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.dto.CreateAccountRequest;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;

@Service
public class CreateAccountAction {
    private final AccountRepository accounts;
    private final UserRepository users;

    public CreateAccountAction(AccountRepository accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional
    public Account execute(CreateAccountRequest request) {
        if (!users.existsById(request.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        BigDecimal amount = request.currentAmount() == null ? BigDecimal.ZERO : request.currentAmount();
        return accounts.save(new Account(request.userId(), request.name().strip(), amount));
    }
}
