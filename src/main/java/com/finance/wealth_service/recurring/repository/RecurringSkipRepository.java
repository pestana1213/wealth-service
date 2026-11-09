package com.finance.wealth_service.recurring.repository;

import com.finance.wealth_service.recurring.entity.RecurringSkip;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringSkipRepository extends JpaRepository<RecurringSkip, UUID> {
}
