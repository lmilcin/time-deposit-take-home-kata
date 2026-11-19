package org.ikigaidigital.domain.interest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ikigaidigital.domain.interest.AccrualTestStates.state;

/** Deposit made 2026-01-10: days 1-30 until 2026-02-08, accrual starts 2026-02-09, first due 2026-03-09. */
class BasicPlanAccrualStrategyTest {

    private static final LocalDate DEPOSITED = LocalDate.of(2026, 1, 10);

    private final BasicPlanAccrualStrategy strategy = new BasicPlanAccrualStrategy();

    @Test
    void appliesToBasicPlan() {
        assertThat(strategy.planType()).isEqualTo("basic");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-10", "2026-02-08", "2026-02-09", "2026-03-08"})
    void notDueBeforeFirstMonthOfAccrualCompletes(String accountingDate) {
        LocalDate date = LocalDate.parse(accountingDate);
        assertThat(strategy.isDue(state("basic", DEPOSITED, date, null), date)).isFalse();
    }

    @Test
    void dueOneCalendarMonthAfterAccrualStarts() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        assertThat(strategy.isDue(state("basic", DEPOSITED, date, null), date)).isTrue();
    }

    @Test
    void notDueAgainForTheSameAccountingDate() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        assertThat(strategy.isDue(state("basic", DEPOSITED, date, date), date)).isFalse();
    }

    @Test
    void dueEveryCalendarMonth() {
        LocalDate lastApplied = LocalDate.of(2026, 3, 9);
        LocalDate dayBefore = LocalDate.of(2026, 4, 8);
        LocalDate anniversary = LocalDate.of(2026, 4, 9);
        assertThat(strategy.isDue(state("basic", DEPOSITED, dayBefore, lastApplied), dayBefore)).isFalse();
        assertThat(strategy.isDue(state("basic", DEPOSITED, anniversary, lastApplied), anniversary)).isTrue();
    }

    @Test
    void catchesUpAMissedAnniversary() {
        LocalDate dayAfterAnniversary = LocalDate.of(2026, 3, 10);
        assertThat(strategy.isDue(state("basic", DEPOSITED, dayAfterAnniversary, null), dayAfterAnniversary)).isTrue();
    }

    @Test
    void anniversariesAtMonthEndDoNotDrift() {
        LocalDate deposited = LocalDate.of(2026, 1, 1); // accrual starts 2026-01-31
        LocalDate feb27 = LocalDate.of(2026, 2, 27);
        LocalDate feb28 = LocalDate.of(2026, 2, 28);
        LocalDate mar28 = LocalDate.of(2026, 3, 28);
        LocalDate mar31 = LocalDate.of(2026, 3, 31);

        assertThat(strategy.isDue(state("basic", deposited, feb27, null), feb27)).isFalse();
        assertThat(strategy.isDue(state("basic", deposited, feb28, null), feb28)).isTrue();
        assertThat(strategy.isDue(state("basic", deposited, mar28, feb28), mar28)).isFalse();
        assertThat(strategy.isDue(state("basic", deposited, mar31, feb28), mar31)).isTrue();
    }
}
