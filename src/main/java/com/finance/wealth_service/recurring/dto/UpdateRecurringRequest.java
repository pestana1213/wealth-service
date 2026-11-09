package com.finance.wealth_service.recurring.dto;

import com.finance.wealth_service.recurring.entity.RecurringFrequency;
import com.finance.wealth_service.recurring.entity.RecurringMode;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Omitted fields are left unchanged.
 *
 * <p>The accounts, buckets and {@code endsOn} can be removed, so they follow JSON Merge
 * Patch (RFC 7396) as in {@code UpdateGoalRequest}: omitted keeps the value, sent as
 * {@code null} removes it. Jackson calls a setter only for properties present in the
 * body, explicit nulls included, so each of those setters notes that the field was sent.
 * The other fields cannot be removed, so {@code null} just means "keep".
 */
public class UpdateRecurringRequest {
    // Null keeps the name; when sent it must have a non-blank character.
    @Size(max = 255)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String name;

    private UUID fromAccountId;
    private boolean fromAccountIdSent;

    private UUID fromBucketId;
    private boolean fromBucketIdSent;

    private UUID toAccountId;
    private boolean toAccountIdSent;

    private UUID toBucketId;
    private boolean toBucketIdSent;

    @Positive
    @Digits(integer = 17, fraction = 2)
    private BigDecimal transactionAmount;

    private TransactionCategory category;

    private RecurringFrequency frequency;

    @Min(1)
    @Max(31)
    private Integer day;

    private LocalDate startsOn;

    private LocalDate endsOn;
    private boolean endsOnSent;

    private RecurringMode mode;

    public void setName(String name) {
        this.name = name;
    }

    public void setFromAccountId(UUID fromAccountId) {
        this.fromAccountId = fromAccountId;
        this.fromAccountIdSent = true;
    }

    public void setFromBucketId(UUID fromBucketId) {
        this.fromBucketId = fromBucketId;
        this.fromBucketIdSent = true;
    }

    public void setToAccountId(UUID toAccountId) {
        this.toAccountId = toAccountId;
        this.toAccountIdSent = true;
    }

    public void setToBucketId(UUID toBucketId) {
        this.toBucketId = toBucketId;
        this.toBucketIdSent = true;
    }

    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public void setCategory(TransactionCategory category) {
        this.category = category;
    }

    public void setFrequency(RecurringFrequency frequency) {
        this.frequency = frequency;
    }

    public void setDay(Integer day) {
        this.day = day;
    }

    public void setStartsOn(LocalDate startsOn) {
        this.startsOn = startsOn;
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
        this.endsOnSent = true;
    }

    public void setMode(RecurringMode mode) {
        this.mode = mode;
    }

    /** The new name, or null to keep the current one. */
    public String name() {
        return name;
    }

    public UUID fromAccountId() {
        return fromAccountId;
    }

    /** True when the body named fromAccountId, even as null. */
    public boolean fromAccountIdSent() {
        return fromAccountIdSent;
    }

    public UUID fromBucketId() {
        return fromBucketId;
    }

    /** True when the body named fromBucketId, even as null. */
    public boolean fromBucketIdSent() {
        return fromBucketIdSent;
    }

    public UUID toAccountId() {
        return toAccountId;
    }

    /** True when the body named toAccountId, even as null. */
    public boolean toAccountIdSent() {
        return toAccountIdSent;
    }

    public UUID toBucketId() {
        return toBucketId;
    }

    /** True when the body named toBucketId, even as null. */
    public boolean toBucketIdSent() {
        return toBucketIdSent;
    }

    /** The new amount, or null to keep the current one. */
    public BigDecimal transactionAmount() {
        return transactionAmount;
    }

    /** The new category, or null to keep the current one. */
    public TransactionCategory category() {
        return category;
    }

    /** The new frequency, or null to keep the current one. */
    public RecurringFrequency frequency() {
        return frequency;
    }

    /** The new day, or null to keep the current one. */
    public Integer day() {
        return day;
    }

    /** The new start date, or null to keep the current one. */
    public LocalDate startsOn() {
        return startsOn;
    }

    /** The new end date; null with {@link #endsOnSent()} means it never ends. */
    public LocalDate endsOn() {
        return endsOn;
    }

    /** True when the body named endsOn, even as null. */
    public boolean endsOnSent() {
        return endsOnSent;
    }

    /** The new mode, or null to keep the current one. */
    public RecurringMode mode() {
        return mode;
    }

    /** True when the update touches the schedule, so {@code nextOn} must be recomputed. */
    public boolean changesSchedule() {
        return frequency != null || day != null || startsOn != null || endsOnSent;
    }
}
