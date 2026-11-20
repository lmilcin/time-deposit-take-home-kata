package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.AccruingTimeDeposit;

import java.time.LocalDate;

/**
 * Student plan: 3% a year, paid monthly, for the first year only; no accrual for the first 30 days, then due
 * every calendar month.
 */
public final class StudentPlanAccrualStrategy implements InterestAccrualStrategy {

    public static final String PLAN_TYPE = "student";

    private static final int WAITING_DAYS = 30;

    private static final double ANNUAL_RATE = 0.03;

    /** No interest once the deposit is a year old, as in the original calculator ({@code days < 366}). */
    private static final int LAST_DAY_WITH_INTEREST = 365;

    @Override
    public String planType() {
        return PLAN_TYPE;
    }

    @Override
    public boolean isDue(AccruingTimeDeposit timeDeposit, LocalDate accountingDate) {
        return MonthlyAccrualSchedule.isDue(timeDeposit, accountingDate, WAITING_DAYS);
    }

    @Override
    public double monthlyInterest(TimeDeposit timeDeposit) {
        return timeDeposit.getDays() > LAST_DAY_WITH_INTEREST ? 0 : timeDeposit.getBalance() * ANNUAL_RATE / 12;
    }
}
