package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.InterestAccrualState;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Selects the {@link InterestAccrualStrategy} for a time deposit by its plan type. */
public class InterestAccrualStrategies {

    private final Map<String, InterestAccrualStrategy> byPlanType;

    /** @throws IllegalArgumentException if more than one strategy is defined for the same plan type */
    public InterestAccrualStrategies(List<InterestAccrualStrategy> strategies) {
        this.byPlanType = strategies.stream().collect(Collectors.toUnmodifiableMap(
                InterestAccrualStrategy::planType,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalArgumentException("Multiple interest accrual strategies for plan type '"
                            + first.planType() + "'");
                }));
    }

    /**
     * Unknown plan types fail the process instead of being skipped. This intentionally deviates from
     * {@code TimeDepositCalculator.updateBalance}, which silently applies no interest to them.
     *
     * @throws UnknownPlanTypeException if no strategy is defined for the deposit's plan type
     */
    public boolean isDue(InterestAccrualState state, LocalDate accountingDate) {
        String planType = state.timeDeposit().getPlanType();
        InterestAccrualStrategy strategy = byPlanType.get(planType);
        if (strategy == null) {
            throw new UnknownPlanTypeException(planType, state.timeDeposit().getId());
        }
        return strategy.isDue(state, accountingDate);
    }
}
