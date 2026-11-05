package com.finance.wealth_service.goal.repository;

import com.finance.wealth_service.goal.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    @Query("select g from Goal g where g.bucketId in (select b.id from Bucket b where b.userId = :userId and b.archived = false) order by g.goalObjective")
    List<Goal> findByUserId(@Param("userId") UUID userId);

    /** The goal, if its bucket belongs to the caller. */
    @Query(value = """
            SELECT g.* FROM goals g
            JOIN buckets b ON b.id = g.bucket_id
            JOIN users u ON u.id = b.user_id
            WHERE g.id = :goalId AND u.subject = :subject AND u.issuer = :issuer""",
            nativeQuery = true)
    Optional<Goal> findOwnedBy(@Param("goalId") UUID goalId,
                               @Param("subject") String subject,
                               @Param("issuer") String issuer);
}
