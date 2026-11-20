package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;

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
     * Unknown plan types fail the process instead of being skipped. This intentionally deviates from the
     * original {@code TimeDepositCalculator.updateBalance}, which silently applied no interest to them.
     *
     * @throws UnknownPlanTypeException if no strategy is defined for the deposit's plan type
     */
    public InterestAccrualStrategy strategyFor(TimeDeposit timeDeposit) {
        InterestAccrualStrategy strategy = byPlanType.get(timeDeposit.getPlanType());
        if (strategy == null) {
            throw new UnknownPlanTypeException(timeDeposit.getPlanType(), timeDeposit.getId());
        }
        return strategy;
    }
}
