package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.AccruingTimeDeposit;

import java.time.LocalDate;

/**
 * Defines, for one plan type, when monthly interest is due for a time deposit and how much it is.
 * Used by {@code TimeDepositCalculator.updateBalance}.
 */
public interface InterestAccrualStrategy {

    /** The plan type this strategy applies to, as stored in {@code time_deposits.plan_type}. */
    String planType();

    /**
     * @param timeDeposit    the time deposit with {@code days} as of {@code accountingDate} (before this run's increment)
     * @param accountingDate the accounting date of the batch run
     * @return {@code true} if the monthly interest calculation must run for the deposit on this accounting date
     */
    boolean isDue(AccruingTimeDeposit timeDeposit, LocalDate accountingDate);

    /**
     * Monthly interest for the deposit, unrounded. Only called when {@link #isDue} is {@code true}.
     *
     * @param timeDeposit the time deposit with {@code days} as of the accounting date (before this run's increment)
     */
    double monthlyInterest(TimeDeposit timeDeposit);
}
