package com.finance.wealth_service.recurring.repository;

import com.finance.wealth_service.recurring.entity.Recurring;

import java.time.LocalDate;
import java.util.List;
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

    @Query(value = """
        SELECT r.* FROM recurring r
        JOIN users u ON u.id = r.user_id
        WHERE u.subject = :sub AND u.issuer = :issuer
        ORDER BY r.next_on NULLS LAST, r.name, r.id""",
        nativeQuery = true)
    List<Recurring> findAllForUser(@Param("sub") String sub,
                                   @Param("issuer") String issuer);

    @Query(value = """
        SELECT r.* FROM recurring r
        JOIN users u ON u.id = r.user_id
        WHERE u.subject = :sub AND u.issuer = :issuer
        AND r.mode = 'CONFIRM' AND r.next_on <= :today""",
        nativeQuery = true)
    List<Recurring> findDueForUser(@Param("sub") String sub,
                                   @Param("issuer") String issuer,
                                   @Param("today") LocalDate today);
}
