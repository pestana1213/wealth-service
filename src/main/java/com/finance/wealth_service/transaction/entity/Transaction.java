package com.finance.wealth_service.transaction.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider_account_id")
    private UUID providerAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "source_bucket_id")
    private UUID sourceBucketId;

    @Column(name = "destination_bucket_id")
    private UUID destinationBucketId;

    @NotNull
    @Positive
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal transactionAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionCategory category;

    @NotNull
    @Column(nullable = false)
    private LocalDate occurredAt;

    private String description;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reversal_of", updatable = false)
    private UUID reversalOf;

    @Column(name = "recurring_id", updatable = false)
    private UUID recurringId;

    @Column(name = "occurrence_on", updatable = false)
    private LocalDate occurrenceOn;

    protected Transaction() {
    }

    public Transaction(UUID userId, UUID providerAccountId, UUID destinationAccountId, UUID sourceBucketId, UUID destinationBucketId, BigDecimal transactionAmount, TransactionType type, TransactionCategory category, LocalDate occurredAt, String description) {
        this(userId, providerAccountId, destinationAccountId, sourceBucketId, destinationBucketId,
                transactionAmount, type, category, occurredAt, description, null);
    }

    public Transaction(UUID userId, UUID providerAccountId, UUID destinationAccountId, UUID sourceBucketId, UUID destinationBucketId, BigDecimal transactionAmount, TransactionType type, TransactionCategory category, LocalDate occurredAt, String description, UUID reversalOf) {
        this.reversalOf = reversalOf;
        this.userId = userId;
        this.providerAccountId = providerAccountId;
        this.destinationAccountId = destinationAccountId;
        this.sourceBucketId = sourceBucketId;
        this.destinationBucketId = destinationBucketId;
        this.transactionAmount = transactionAmount;
        this.type = type;
        this.category = category;
        this.occurredAt = occurredAt;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getReversalOf() {
        return reversalOf;
    }

    /** Marks this transaction as the given occurrence of a recurring; call before it is saved. */
    public void linkToRecurring(UUID recurringId, LocalDate occurrenceOn) {
        this.recurringId = Objects.requireNonNull(recurringId, "recurringId is required");
        this.occurrenceOn = Objects.requireNonNull(occurrenceOn, "occurrenceOn is required");
    }

    public UUID getRecurringId() {
        return recurringId;
    }

    public LocalDate getOccurrenceOn() {
        return occurrenceOn;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProviderAccountId() {
        return providerAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public UUID getSourceBucketId() {
        return sourceBucketId;
    }

    public UUID getDestinationBucketId() {
        return destinationBucketId;
    }

    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public LocalDate getOccurredAt() {
        return occurredAt;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
