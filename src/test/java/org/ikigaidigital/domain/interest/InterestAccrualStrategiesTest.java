package org.ikigaidigital.domain.interest;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.ikigaidigital.domain.interest.AccrualTestStates.state;

class InterestAccrualStrategiesTest {

    private static final LocalDate DEPOSITED = LocalDate.of(2026, 1, 10);
    private static final LocalDate ACCOUNTING_DATE = LocalDate.of(2026, 3, 9);

    private final InterestAccrualStrategies strategies = new InterestAccrualStrategies(List.of(
            new BasicPlanAccrualStrategy(), new StudentPlanAccrualStrategy(), new PremiumPlanAccrualStrategy()));

    @Test
    void delegatesToTheStrategyOfThePlanType() {
        // on this date the 30-day plans are due, but premium (45 days) is not yet
        assertThat(strategies.isDue(state("basic", DEPOSITED, ACCOUNTING_DATE, null), ACCOUNTING_DATE)).isTrue();
        assertThat(strategies.isDue(state("student", DEPOSITED, ACCOUNTING_DATE, null), ACCOUNTING_DATE)).isTrue();
        assertThat(strategies.isDue(state("premium", DEPOSITED, ACCOUNTING_DATE, null), ACCOUNTING_DATE)).isFalse();
    }

    @Test
    void failsForUnknownPlanType() {
        assertThatThrownBy(() -> strategies.isDue(state("gold", DEPOSITED, ACCOUNTING_DATE, null), ACCOUNTING_DATE))
                .isInstanceOf(UnknownPlanTypeException.class)
                .hasMessageContaining("gold");
    }

    @Test
    void rejectsMultipleStrategiesForTheSamePlanType() {
        assertThatThrownBy(() -> new InterestAccrualStrategies(List.of(
                new BasicPlanAccrualStrategy(), new BasicPlanAccrualStrategy())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basic");
    }
}
