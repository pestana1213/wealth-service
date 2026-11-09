package com.finance.wealth_service.recurring.actions;

import com.finance.wealth_service.exception.ConflictException;
import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.entity.RecurringMode;

import java.time.LocalDate;

final class DueOccurrence {

    private DueOccurrence() {
    }

    static LocalDate of(Recurring recurring, LocalDate today) {
        if (recurring.getMode() != RecurringMode.CONFIRM)
            throw new ConflictException("This recurring is recorded automatically");
        LocalDate next = recurring.getNextOn();
        if (next == null)
            throw new ConflictException("This recurring has finished");
        if (next.isAfter(today))
            throw new ConflictException("The next occurrence, on " + next + ", is not due yet");
        return next;
    }
}
