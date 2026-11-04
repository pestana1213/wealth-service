package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.dto.CreateUserRequest;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateUserAction {
    private final UserRepository users;

    public CreateUserAction(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public User execute(CreateUserRequest request) {
        // if (users.existsByEmailIgnoreCase(request.email())) {
        //     throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        // }
        try {
            return users.saveAndFlush(new User());
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && "users_email_unique".equals(violation.getConstraintName())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered", exception);
                }
            }
            throw exception;
        }
    }
}
