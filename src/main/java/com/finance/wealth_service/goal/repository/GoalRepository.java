package com.finance.wealth_service.goal.repository;

import com.finance.wealth_service.goal.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    @Query("select g from Goal g where g.bucketId in (select b.id from Bucket b where b.userId = :userId) order by g.goalObjective")
    List<Goal> findByUserId(@Param("userId") UUID userId);
}
