package org.ikigaidigital;

import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;
import org.ikigaidigital.domain.interest.InterestAccrualStrategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Applies monthly interest to time deposits for one accounting date, delegating both when interest is due and
 * how much it is to the {@link InterestAccrualStrategy} of each deposit's plan type.
 * <p>
 * The {@link #updateBalance(List)} signature is kept as required, but its behaviour deliberately differs from the
 * original implementation, which added interest on every call once a deposit passed its threshold, and silently
 * skipped unknown plan types:
 * <ul>
 *     <li>interest is applied once per calendar month per deposit, as the business rules require monthly interest
 *     and the batch runs daily; the accounting date and {@code lastInterestAppliedOn} needed for that decision are
 *     supplied by the constructor and {@link AccruingTimeDeposit}, as the method signature cannot carry them;</li>
 *     <li>an unknown plan type fails the whole call before any deposit is changed.</li>
 * </ul>
 * Rates and rounding are unchanged: annual rate / 12 of the balance, rounded HALF_UP to 2 decimal places.
 */
public class TimeDepositCalculator {

    private final InterestAccrualStrategies strategies;
    private final LocalDate accountingDate;

    /**
     * @param accountingDate the accounting date of the batch run; never derived from the system clock
     */
    public TimeDepositCalculator(InterestAccrualStrategies strategies, LocalDate accountingDate) {
        this.strategies = Objects.requireNonNull(strategies, "strategies");
        this.accountingDate = Objects.requireNonNull(accountingDate, "accountingDate");
    }

    /**
     * Adds the monthly interest to the balance of each deposit for which it is due on the accounting date.
     * For an {@link AccruingTimeDeposit}, the accounting date is recorded as {@code lastInterestAppliedOn} whenever
     * the monthly calculation runs, even if the interest is zero. A plain {@link TimeDeposit} is treated as never
     * having had interest applied.
     *
     * @throws org.ikigaidigital.domain.interest.UnknownPlanTypeException if any deposit has an unknown plan type;
     *                                                                    no deposit is changed in that case
     */
    public void updateBalance(List<TimeDeposit> xs) {
        // Resolve every strategy first, so an unknown plan type fails before any balance is changed.
        List<InterestAccrualStrategy> resolved = xs.stream().map(strategies::strategyFor).toList();

        for (int i = 0; i < xs.size(); i++) {
            TimeDeposit timeDeposit = xs.get(i);
            InterestAccrualStrategy strategy = resolved.get(i);
            if (!strategy.isDue(accruing(timeDeposit), accountingDate)) {
                continue;
            }

            double interest = strategy.monthlyInterest(timeDeposit);
            double newBalance = timeDeposit.getBalance() + (new BigDecimal(interest).setScale(2, RoundingMode.HALF_UP)).doubleValue();
            timeDeposit.setBalance(newBalance);
            if (timeDeposit instanceof AccruingTimeDeposit accruing) {
                accruing.setLastInterestAppliedOn(accountingDate);
            }
        }
    }

    private static AccruingTimeDeposit accruing(TimeDeposit timeDeposit) {
        return timeDeposit instanceof AccruingTimeDeposit accruing
                ? accruing
                : new AccruingTimeDeposit(timeDeposit.getId(), timeDeposit.getPlanType(), timeDeposit.getBalance(),
                        timeDeposit.getDays(), null);
    }
}
