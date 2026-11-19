package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.InterestAccrualState;

import java.time.LocalDate;

/** Decides, for one plan type, whether monthly interest is due for a time deposit on an accounting date. */
public interface InterestAccrualStrategy {

    /** The plan type this strategy applies to, as stored in {@code time_deposits.plan_type}. */
    String planType();

    /**
     * @param state          the time deposit with {@code days} as of {@code accountingDate} (before this run's increment)
     * @param accountingDate the accounting date of the batch run
     * @return {@code true} if the monthly interest calculation must run for the deposit on this accounting date
     */
    boolean isDue(InterestAccrualState state, LocalDate accountingDate);
}
