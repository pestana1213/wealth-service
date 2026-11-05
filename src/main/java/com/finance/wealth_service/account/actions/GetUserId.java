package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.port.UserPort;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetUserId implements UserPort {
    private final UserRepository users;

    public GetUserId(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UUID> findUserId(AuthenticatedUser user) {
        return users.findBySubjectAndIssuer(user.subject(), user.issuer()).map(User::getId);
    }

    @Override
    public UUID requireUserId(AuthenticatedUser user) {
        return findUserId(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
