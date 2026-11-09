package com.finance.wealth_service.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finance.wealth_service.recurring.entity.Recurring;
import com.finance.wealth_service.recurring.entity.RecurringFrequency;
import com.finance.wealth_service.recurring.entity.RecurringMode;
import com.finance.wealth_service.transaction.entity.TransactionCategory;
import com.finance.wealth_service.transaction.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecurringScheduleTests {

    private static Recurring recurring(RecurringFrequency frequency, int day, LocalDate startsOn, LocalDate endsOn) {
        return new Recurring(UUID.randomUUID(), "Renda", UUID.randomUUID(), null, null, null,
                new BigDecimal("820.00"), TransactionCategory.HOUSE, frequency, day, startsOn, endsOn,
                RecurringMode.CONFIRM);
    }

    @Test
    void monthlyDay31IsTheLastDayOfEachMonth() {
        Recurring recurring = recurring(RecurringFrequency.MONTHLY, 31, LocalDate.of(2026, 11, 15), null);

        assertThat(recurring.occurrencesThrough(LocalDate.of(2027, 2, 28))).containsExactly(
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28));
    }

    @Test
    void monthlyStartingAfterTheDayBeginsNextMonth() {
        Recurring recurring = recurring(RecurringFrequency.MONTHLY, 5, LocalDate.of(2026, 11, 6), null);

        assertThat(recurring.getNextOn()).isEqualTo(LocalDate.of(2026, 12, 5));
    }

    @Test
    void weeklyUsesIsoDayOfWeek() {
        // 2026-11-09 is a Monday; day 3 is Wednesday.
        Recurring recurring = recurring(RecurringFrequency.WEEKLY, 3, LocalDate.of(2026, 11, 9), null);

        assertThat(recurring.occurrencesThrough(LocalDate.of(2026, 11, 25))).containsExactly(
                LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 25));
    }

    @Test
    void yearlyTakesTheMonthFromStartsOnAndClampsLeapDays() {
        Recurring recurring = recurring(RecurringFrequency.YEARLY, 29, LocalDate.of(2028, 2, 1), null);

        assertThat(recurring.occurrencesThrough(LocalDate.of(2030, 12, 31))).containsExactly(
                LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28), LocalDate.of(2030, 2, 28));
    }

    @Test
    void stopsAtEndsOn() {
        Recurring recurring = recurring(RecurringFrequency.MONTHLY, 1,
                LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 15));

        assertThat(recurring.occurrencesThrough(LocalDate.of(2027, 6, 1)))
                .containsExactly(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1));

        recurring.advance();
        recurring.advance();
        assertThat(recurring.isFinished()).isTrue();
        assertThat(recurring.occurrencesThrough(LocalDate.of(2027, 6, 1))).isEmpty();
    }

    @Test
    void recognisesItsOccurrences() {
        Recurring recurring = recurring(RecurringFrequency.MONTHLY, 31, LocalDate.of(2026, 11, 1), null);

        assertThat(recurring.isOccurrence(LocalDate.of(2026, 11, 30))).isTrue();
        assertThat(recurring.isOccurrence(LocalDate.of(2026, 11, 29))).isFalse();
        assertThat(recurring.isOccurrence(LocalDate.of(2026, 10, 31))).isFalse();
    }

    @Test
    void rescheduleNeverGoesBackBeforeToday() {
        Recurring recurring = recurring(RecurringFrequency.MONTHLY, 1, LocalDate.of(2026, 9, 1), null);

        recurring.reschedule(RecurringFrequency.MONTHLY, 15, LocalDate.of(2026, 9, 1), null,
                LocalDate.of(2026, 11, 20));

        assertThat(recurring.getNextOn()).isEqualTo(LocalDate.of(2026, 12, 15));
    }

    @Test
    void typeFollowsTheAccountsLikeATransaction() {
        UUID account = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Recurring recurring = recurring(RecurringFrequency.MONTHLY, 1, LocalDate.of(2026, 12, 1), null);

        recurring.setAccounts(null, account, null, null);
        assertThat(recurring.getType()).isEqualTo(TransactionType.INCOME);

        recurring.setAccounts(account, null, null, null);
        assertThat(recurring.getType()).isEqualTo(TransactionType.EXPENSE);

        recurring.setAccounts(account, other, null, null);
        assertThat(recurring.getType()).isEqualTo(TransactionType.TRANSFER);
    }

    @Test
    void rejectsADayOutOfRangeForTheFrequency() {
        assertThatThrownBy(() -> recurring(RecurringFrequency.WEEKLY, 8, LocalDate.of(2026, 11, 9), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> recurring(RecurringFrequency.MONTHLY, 0, LocalDate.of(2026, 11, 9), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
