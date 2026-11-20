package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ikigaidigital.domain.interest.AccrualTestStates.state;

/** Deposit made 2026-01-10: days 1-45 until 2026-02-23, accrual starts 2026-02-24, first due 2026-03-24. */
class PremiumPlanAccrualStrategyTest {

    private static final LocalDate DEPOSITED = LocalDate.of(2026, 1, 10);

    private final PremiumPlanAccrualStrategy strategy = new PremiumPlanAccrualStrategy();

    @Test
    void appliesToPremiumPlan() {
        assertThat(strategy.planType()).isEqualTo("premium");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-23", "2026-02-24", "2026-03-09", "2026-03-23"})
    void notDueBeforeFirstMonthOfAccrualCompletes(String accountingDate) {
        LocalDate date = LocalDate.parse(accountingDate);
        assertThat(strategy.isDue(state("premium", DEPOSITED, date, null), date)).isFalse();
    }

    @Test
    void dueOneCalendarMonthAfterAccrualStarts() {
        LocalDate date = LocalDate.of(2026, 3, 24);
        assertThat(strategy.isDue(state("premium", DEPOSITED, date, null), date)).isTrue();
    }

    @Test
    void notDueAgainForTheSameAccountingDate() {
        LocalDate date = LocalDate.of(2026, 3, 24);
        assertThat(strategy.isDue(state("premium", DEPOSITED, date, date), date)).isFalse();
    }

    @Test
    void dueEveryCalendarMonth() {
        LocalDate lastApplied = LocalDate.of(2026, 3, 24);
        LocalDate dayBefore = LocalDate.of(2026, 4, 23);
        LocalDate anniversary = LocalDate.of(2026, 4, 24);
        assertThat(strategy.isDue(state("premium", DEPOSITED, dayBefore, lastApplied), dayBefore)).isFalse();
        assertThat(strategy.isDue(state("premium", DEPOSITED, anniversary, lastApplied), anniversary)).isTrue();
    }

    @Test
    void monthlyInterestIsOneTwelfthOfFivePercent() {
        assertThat(strategy.monthlyInterest(new TimeDeposit(1, "premium", 12000.00, 74))).isEqualTo(50.00);
    }
}
