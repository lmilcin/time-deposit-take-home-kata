package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ikigaidigital.domain.interest.AccrualTestStates.state;

/** Deposit made 2026-01-10: accrual starts on day 31 (2026-02-09), first due 2026-03-09. */
class StudentPlanAccrualStrategyTest {

    private static final LocalDate DEPOSITED = LocalDate.of(2026, 1, 10);

    private final StudentPlanAccrualStrategy strategy = new StudentPlanAccrualStrategy();

    @Test
    void appliesToStudentPlan() {
        assertThat(strategy.planType()).isEqualTo("student");
    }

    @Test
    void notDueBeforeFirstMonthOfAccrualCompletes() {
        LocalDate date = LocalDate.of(2026, 3, 8);
        assertThat(strategy.isDue(state("student", DEPOSITED, date, null), date)).isFalse();
    }

    @Test
    void dueOneCalendarMonthAfterAccrualStarts() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        assertThat(strategy.isDue(state("student", DEPOSITED, date, null), date)).isTrue();
    }

    @Test
    void notDueAgainForTheSameAccountingDate() {
        LocalDate date = LocalDate.of(2026, 3, 9);
        assertThat(strategy.isDue(state("student", DEPOSITED, date, date), date)).isFalse();
    }

    /** The one-year cut-off only affects the amount (see below), so the monthly calculation still runs. */
    @Test
    void stillDueMonthlyAfterOneYear() {
        LocalDate deposited = LocalDate.of(2025, 1, 10);
        LocalDate date = LocalDate.of(2026, 3, 9);
        assertThat(strategy.isDue(state("student", deposited, date, LocalDate.of(2026, 2, 9)), date)).isTrue();
    }

    @Test
    void monthlyInterestIsOneTwelfthOfThreePercentDuringTheFirstYear() {
        assertThat(strategy.monthlyInterest(new TimeDeposit(1, "student", 12000.00, 365))).isEqualTo(30.00);
    }

    @Test
    void noInterestOnceTheDepositIsAYearOld() {
        assertThat(strategy.monthlyInterest(new TimeDeposit(1, "student", 12000.00, 366))).isZero();
    }
}
