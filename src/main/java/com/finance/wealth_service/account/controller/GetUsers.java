package com.finance.wealth_service.account.controller;

import com.finance.wealth_service.account.entity.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Users")
public class GetUsers {

    private final GetUsers getUsers;

    public GetUsers(GetUsers getUsers) {
        this.getUsers = getUsers;
    }

    @GetMapping("/users")
    @ResponseStatus(HttpStatus.OK)
    public List<User> getUsers() {
        return getUsers.getUsers();
    }
}
