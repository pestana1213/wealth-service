package com.finance.wealth_service.common;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** One scheduled occurrence of a recurring: which recurring, and the day it was due. */
public record Occurrence(UUID recurringId, LocalDate on) {

    public Occurrence {
        Objects.requireNonNull(recurringId, "recurringId is required");
        Objects.requireNonNull(on, "on is required");
    }
}
