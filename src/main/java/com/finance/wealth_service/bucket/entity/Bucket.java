package com.finance.wealth_service.bucket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "buckets")
public class Bucket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(name = "current_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentAmount = new BigDecimal("0.00");

    protected Bucket() {
    }

    public Bucket(UUID userId, String name) {
        this(userId, name, BigDecimal.ZERO);
    }

    public Bucket(UUID userId, String name, BigDecimal currentAmount) {
        this.userId = userId;
        this.name = name;
        setCurrentAmount(currentAmount);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = Objects.requireNonNull(currentAmount, "currentAmount is required")
                .setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
