package com.finance.wealth_service.recurring.entity;

import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A movement that repeats on a schedule. {@code nextOn} is the first occurrence not yet
 * handled (recorded or skipped); it is {@code null} once the schedule is past {@code endsOn}.
 */
@Entity
@Table(name = "recurring")
public class Recurring {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @NotNull
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @NotBlank
    @Column(nullable = false)
    private String name;

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
    private TransactionCategory category;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    @Column(nullable = false)
    private int day;

    @NotNull
    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringMode mode;

    @Column(name = "next_on")
    private LocalDate nextOn;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Recurring() {
    }

    public Recurring(UUID userId, String name, UUID providerAccountId, UUID destinationAccountId,
                     UUID sourceBucketId, UUID destinationBucketId, BigDecimal transactionAmount,
                     TransactionCategory category, RecurringFrequency frequency, int day,
                     LocalDate startsOn, LocalDate endsOn, RecurringMode mode) {
        this.userId = Objects.requireNonNull(userId, "userId is required");
        setName(name);
        setAccounts(providerAccountId, destinationAccountId, sourceBucketId, destinationBucketId);
        setTransactionAmount(transactionAmount);
        setCategory(category);
        setMode(mode);
        applySchedule(frequency, day, startsOn, endsOn);
        this.nextOn = occurrenceOnOrAfter(startsOn);
        this.createdAt = Instant.now();
    }

    /**
     * Changes the schedule. Occurrences are recomputed from {@code today} or from the
     * current {@code nextOn}, whichever is later, so no occurrence is generated in the past.
     * Occurrences still pending before {@code today} are dropped.
     */
    public void reschedule(RecurringFrequency frequency, int day, LocalDate startsOn, LocalDate endsOn,
                           LocalDate today) {
        Objects.requireNonNull(today, "today is required");
        applySchedule(frequency, day, startsOn, endsOn);
        LocalDate from = nextOn == null || today.isAfter(nextOn) ? today : nextOn;
        this.nextOn = occurrenceOnOrAfter(from);
    }

    /** Moves {@code nextOn} to the following occurrence, or to {@code null} past {@code endsOn}. */
    public void advance() {
        if (nextOn != null) {
            nextOn = occurrenceOnOrAfter(nextOn.plusDays(1));
        }
    }

    /** The occurrences from {@code nextOn} up to and including {@code until}, in order. */
    public List<LocalDate> occurrencesThrough(LocalDate until) {
        Objects.requireNonNull(until, "until is required");
        List<LocalDate> occurrences = new ArrayList<>();
        LocalDate occurrence = nextOn;
        while (occurrence != null && !occurrence.isAfter(until)) {
            occurrences.add(occurrence);
            occurrence = occurrenceOnOrAfter(occurrence.plusDays(1));
        }
        return occurrences;
    }

    /** Whether {@code date} is an occurrence of this schedule, handled or not. */
    public boolean isOccurrence(LocalDate date) {
        return date != null && date.equals(occurrenceOnOrAfter(date));
    }

    public boolean isFinished() {
        return nextOn == null;
    }

    /** As in {@code Movement}: no origin is an income, no destination is an expense, both is a transfer. */
    public TransactionType getType() {
        if (providerAccountId == null) return TransactionType.INCOME;
        if (destinationAccountId == null) return TransactionType.EXPENSE;
        return TransactionType.TRANSFER;
    }

    private void applySchedule(RecurringFrequency frequency, int day, LocalDate startsOn, LocalDate endsOn) {
        Objects.requireNonNull(frequency, "frequency is required");
        Objects.requireNonNull(startsOn, "startsOn is required");
        int maxDay = frequency == RecurringFrequency.WEEKLY ? 7 : 31;
        if (day < 1 || day > maxDay) {
            throw new IllegalArgumentException("day must be between 1 and " + maxDay + " for " + frequency);
        }
        if (endsOn != null && endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("endsOn must not be before startsOn");
        }
        this.frequency = frequency;
        this.day = day;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    /** The first occurrence on or after {@code date}, or {@code null} if it falls after {@code endsOn}. */
    private LocalDate occurrenceOnOrAfter(LocalDate date) {
        LocalDate from = date.isBefore(startsOn) ? startsOn : date;
        LocalDate occurrence = switch (frequency) {
            case WEEKLY -> from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)));
            case MONTHLY -> {
                LocalDate inMonth = dayIn(YearMonth.from(from));
                yield inMonth.isBefore(from) ? dayIn(YearMonth.from(from).plusMonths(1)) : inMonth;
            }
            case YEARLY -> {
                YearMonth inYear = YearMonth.of(from.getYear(), startsOn.getMonth());
                LocalDate candidate = dayIn(inYear);
                yield candidate.isBefore(from) ? dayIn(inYear.plusYears(1)) : candidate;
            }
        };
        return endsOn != null && occurrence.isAfter(endsOn) ? null : occurrence;
    }

    /** The configured day in the given month, clamped to its last day (31 = last day). */
    private LocalDate dayIn(YearMonth month) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = name;
    }

    public void setAccounts(UUID providerAccountId, UUID destinationAccountId,
                            UUID sourceBucketId, UUID destinationBucketId) {
        this.providerAccountId = providerAccountId;
        this.destinationAccountId = destinationAccountId;
        this.sourceBucketId = sourceBucketId;
        this.destinationBucketId = destinationBucketId;
    }

    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = Objects.requireNonNull(transactionAmount, "transactionAmount is required")
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public void setCategory(TransactionCategory category) {
        this.category = Objects.requireNonNull(category, "category is required");
    }

    public void setMode(RecurringMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode is required");
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

    public TransactionCategory getCategory() {
        return category;
    }

    public RecurringFrequency getFrequency() {
        return frequency;
    }

    public int getDay() {
        return day;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public RecurringMode getMode() {
        return mode;
    }

    public LocalDate getNextOn() {
        return nextOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
