package com.finance.wealth_service.goal.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Omitted fields are left unchanged. A goal's bucket is fixed: pointing a goal at
 * another bucket means a different goal.
 *
 * <p>{@code goalObjective} follows JSON Merge Patch (RFC 7396): omitted keeps the
 * date, sent as {@code null} removes it. A record cannot tell those two apart, as
 * both arrive as {@code null}, so this is a class whose setter notes that the field
 * was sent. Jackson calls a setter only for properties present in the body,
 * explicit nulls included.
 */
public class UpdateGoalRequest {
    @Positive
    @Digits(integer = 17, fraction = 2)
    private BigDecimal goalAmount;

    private LocalDate goalObjective;
    private boolean goalObjectiveSent;

    public void setGoalAmount(BigDecimal goalAmount) {
        this.goalAmount = goalAmount;
    }

    public void setGoalObjective(LocalDate goalObjective) {
        this.goalObjective = goalObjective;
        this.goalObjectiveSent = true;
    }

    /** The new target amount, or null to keep the current one. */
    public BigDecimal goalAmount() {
        return goalAmount;
    }

    /** True when the body named goalObjective, even as null. */
    public boolean goalObjectiveSent() {
        return goalObjectiveSent;
    }

    /** The new date; null with {@link #goalObjectiveSent()} means remove it. */
    public LocalDate goalObjective() {
        return goalObjective;
    }
}
