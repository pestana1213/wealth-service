package com.finance.wealth_service.recurring.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** An occurrence the user chose to skip, so it is never due again. */
@Entity
@Table(name = "recurring_skips")
public class RecurringSkip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "recurring_id", nullable = false, updatable = false)
    private UUID recurringId;

    @NotNull
    @Column(name = "occurrence_on", nullable = false, updatable = false)
    private LocalDate occurrenceOn;

    protected RecurringSkip() {
    }

    public RecurringSkip(UUID recurringId, LocalDate occurrenceOn) {
        this.recurringId = Objects.requireNonNull(recurringId, "recurringId is required");
        this.occurrenceOn = Objects.requireNonNull(occurrenceOn, "occurrenceOn is required");
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecurringId() {
        return recurringId;
    }

    public LocalDate getOccurrenceOn() {
        return occurrenceOn;
    }
}
