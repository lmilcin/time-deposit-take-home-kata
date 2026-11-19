package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.InterestAccrualState;

import java.time.LocalDate;

/** Student plan: interest is not accrued for the first 30 days, then it is due every calendar month. */
public final class StudentPlanAccrualStrategy implements InterestAccrualStrategy {

    public static final String PLAN_TYPE = "student";

    private static final int WAITING_DAYS = 30;

    @Override
    public String planType() {
        return PLAN_TYPE;
    }

    @Override
    public boolean isDue(InterestAccrualState state, LocalDate accountingDate) {
        return MonthlyAccrualSchedule.isDue(state, accountingDate, WAITING_DAYS);
    }
}
