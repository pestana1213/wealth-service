package com.finance.wealth_service.account.repository;

import com.finance.wealth_service.account.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

}
