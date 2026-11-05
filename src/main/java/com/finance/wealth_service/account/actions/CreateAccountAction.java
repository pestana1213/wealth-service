package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.AccountType;
import com.finance.wealth_service.account.dto.CreateAccountRequest;
import com.finance.wealth_service.account.entity.Account;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.AccountRepository;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Optional;

@Service
public class CreateAccountAction {
    private final AccountRepository accounts;
    private final UserRepository users;

    public CreateAccountAction(AccountRepository accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    @Transactional
    public Account execute(AuthenticatedUser user, CreateAccountRequest request) {
        AccountType type = AccountType.SAVINGS;
        Optional<User> optionalUser = users.findBySubjectAndIssuer(user.subject(), user.issuer());
        if (optionalUser.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        BigDecimal amount = request.currentAmount() == null ? BigDecimal.ZERO : request.currentAmount();
        if (request.type() != null) {
            type = request.type();
        }
        return accounts.save(new Account(optionalUser.get().getId(), request.name().strip(), amount, type));
    }
}
