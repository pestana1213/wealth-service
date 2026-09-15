package com.finance.wealth_service.goal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "bucket_id", nullable = false)
    private UUID bucketId;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal goalAmount;

    private LocalDate goalObjective;

    protected Goal() {
    }

    public Goal(UUID bucketId, BigDecimal goalAmount, LocalDate goalObjective) {
        this.bucketId = bucketId;
        this.goalAmount = goalAmount;
        this.goalObjective = goalObjective;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBucketId() {
        return bucketId;
    }

    public BigDecimal getGoalAmount() {
        return goalAmount;
    }

    public LocalDate getGoalObjective() {
        return goalObjective;
    }
}
