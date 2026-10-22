package com.finance.wealth_service.account.entity;

import com.finance.wealth_service.account.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "accounts")
public class Account {

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "type", nullable = false, columnDefinition = "account_type")
    private AccountType type;

    protected Account() {
    }

    public Account(UUID userId, String name) {
        this(userId, name, BigDecimal.ZERO);
    }

    public Account(UUID userId, String name, BigDecimal currentAmount) {
        this(userId, name, currentAmount, AccountType.SAVINGS);
    }

    public Account(UUID userId, String name, BigDecimal currentAmount, AccountType type) {
        this.userId = userId;
        this.name = name;
        setCurrentAmount(currentAmount);
        this.type = type;
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

    public AccountType getType() {
        return type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(AccountType type) {
        this.type = Objects.requireNonNull(type, "type is required");
    }

    public void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = Objects.requireNonNull(currentAmount, "currentAmount is required")
                .setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
