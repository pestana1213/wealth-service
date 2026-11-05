package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.UserRepository;
import com.finance.wealth_service.auth.AuthenticatedUser;
import com.finance.wealth_service.auth.UserInfoClient;
import com.finance.wealth_service.auth.UserProfile;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.finance.wealth_service.exception.ConflictException;

import java.util.Optional;

@Service
public class GetOrCreateUserAction {
    private final UserRepository users;
    private final UserInfoClient userInfo;
    private final TransactionTemplate transactionTemplate;

    public GetOrCreateUserAction(UserRepository users, UserInfoClient userInfo, TransactionTemplate transactionTemplate) {
        this.users = users;
        this.userInfo = userInfo;
        this.transactionTemplate = transactionTemplate;
    }

    public User execute(AuthenticatedUser caller, String accessToken) {
        Optional<User> existing = users.findBySubjectAndIssuer(caller.subject(), caller.issuer());
        if (existing.isPresent()) {
            return existing.get();
        }
        UserProfile profile = userInfo.fetch(accessToken);
        try {
            return transactionTemplate.execute(_ ->
                    users.saveAndFlush(new User(caller.issuer(), caller.subject(), profile.name(), profile.email())));
        } catch (DataIntegrityViolationException exception) {
            String constraint = constraintName(exception);
            if ("users_identity_unique".equals(constraint)) {
                return users.findBySubjectAndIssuer(caller.subject(), caller.issuer()).orElseThrow(() -> exception);
            }
            if ("users_email_unique".equals(constraint)) {
                throw new ConflictException("Email already registered", exception);
            }
            throw exception;
        }
    }

    private static String constraintName(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }
}
