package com.finance.wealth_service.account.actions;

import com.finance.wealth_service.account.entity.User;
import com.finance.wealth_service.account.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetUsersAction {

    private final UserRepository userRepository;

    public GetUsersAction(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }
}
