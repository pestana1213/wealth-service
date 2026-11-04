package com.finance.wealth_service.account.controller;

import com.finance.wealth_service.account.actions.CreateUserAction;
import com.finance.wealth_service.account.dto.CreateUserRequest;
import com.finance.wealth_service.account.dto.CreateUserResponse;
import com.finance.wealth_service.account.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Users", description = "User registration")
public class CreateUser {
    private final CreateUserAction action;

    public CreateUser(CreateUserAction action) {
        this.action = action;
    }

    @PostMapping("/users")
    @Operation(summary = "Create a user", description = "Registers a user with a unique email address.")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        User user = action.execute(request);
        return new CreateUserResponse(user.getId(), "", "");
    }
}
