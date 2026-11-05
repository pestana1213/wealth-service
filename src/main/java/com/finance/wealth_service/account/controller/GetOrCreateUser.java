package com.finance.wealth_service.account.controller;

import com.finance.wealth_service.account.actions.GetOrCreateUserAction;
import com.finance.wealth_service.account.dto.UserResponse;
import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.auth.UserAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Users", description = "The signed-in user")
public class GetOrCreateUser {
    private final GetOrCreateUserAction action;

    public GetOrCreateUser(GetOrCreateUserAction action) {
        this.action = action;
    }

    @PostMapping("/users/me")
    @Operation(summary = "Get or create the signed-in user",
            description = "Returns the local user for the token's identity, creating it on first use with the name and email from the identity provider.")
    public UserResponse getOrCreate(UserAuthentication authentication) {
        User user = action.execute(authentication.getPrincipal(), authentication.getCredentials().getTokenValue());
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
