package com.finance.wealth_service.recurring.repository;

import com.finance.wealth_service.recurring.entity.Recurring;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringRepository extends JpaRepository<Recurring, UUID> {

    @Query(value = """
        SELECT r.* FROM recurring r
        JOIN users u ON u.id = r.user_id
        WHERE r.id = :id AND u.subject = :sub AND u.issuer = :issuer""",
        nativeQuery = true)
    Optional<Recurring> findRecurringByIdForUser(@Param("sub") String sub,
                                                 @Param("issuer") String issuer,
                                                 @Param("id") UUID recurringId);
}
