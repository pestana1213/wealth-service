package com.finance.wealth_service.valuation.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "account_valuations")
public class AccountValuation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    // The account balance on valuedAt. It can be negative, as an account can be
    // overdrawn. Null on rows recorded before the column existed.
    @Column(name = "invested_money", precision = 19, scale = 2)
    private BigDecimal investedMoney;

    @NotNull
    @Column(name = "valued_at", nullable = false)
    private LocalDate valuedAt;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AccountValuation() {
    }

    public AccountValuation(UUID accountId, BigDecimal value, BigDecimal investedMoney, LocalDate valuedAt) {
        this.accountId = accountId;
        this.value = value.setScale(2, java.math.RoundingMode.UNNECESSARY);
        this.valuedAt = valuedAt;
        this.investedMoney = investedMoney;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getValue() {
        return value;
    }

    public LocalDate getValuedAt() {
        return valuedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getInvestedMoney() {
        return investedMoney;
    }
}
