package com.finance.wealth_service.goal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

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
        setGoalAmount(goalAmount);
        this.goalObjective = goalObjective;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBucketId() {
        return bucketId;
    }

    public void setGoalAmount(BigDecimal goalAmount) {
        this.goalAmount = Objects.requireNonNull(goalAmount, "goalAmount is required")
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public void setGoalObjective(LocalDate goalObjective) {
        this.goalObjective = goalObjective;
    }

    public BigDecimal getGoalAmount() {
        return goalAmount;
    }

    public LocalDate getGoalObjective() {
        return goalObjective;
    }
}
