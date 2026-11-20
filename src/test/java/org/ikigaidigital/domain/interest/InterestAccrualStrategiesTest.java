package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterestAccrualStrategiesTest {

    private final InterestAccrualStrategies strategies = new InterestAccrualStrategies(List.of(
            new BasicPlanAccrualStrategy(), new StudentPlanAccrualStrategy(), new PremiumPlanAccrualStrategy()));

    @Test
    void selectsTheStrategyOfThePlanType() {
        assertThat(strategies.strategyFor(deposit("basic"))).isInstanceOf(BasicPlanAccrualStrategy.class);
        assertThat(strategies.strategyFor(deposit("student"))).isInstanceOf(StudentPlanAccrualStrategy.class);
        assertThat(strategies.strategyFor(deposit("premium"))).isInstanceOf(PremiumPlanAccrualStrategy.class);
    }

    @Test
    void failsForUnknownPlanType() {
        assertThatThrownBy(() -> strategies.strategyFor(deposit("gold")))
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

    private static TimeDeposit deposit(String planType) {
        return new TimeDeposit(1, planType, 1000.00, 60);
    }
}
