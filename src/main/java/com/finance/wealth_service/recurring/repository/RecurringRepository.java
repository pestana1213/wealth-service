package com.finance.wealth_service.recurring.repository;

import com.finance.wealth_service.recurring.entity.Recurring;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringRepository extends JpaRepository<Recurring, UUID> {}
