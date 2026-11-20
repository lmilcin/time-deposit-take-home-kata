package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.AccruingTimeDeposit;

import java.time.LocalDate;

/** Basic plan: 1% a year, paid monthly; no accrual for the first 30 days, then due every calendar month. */
public final class BasicPlanAccrualStrategy implements InterestAccrualStrategy {

    public static final String PLAN_TYPE = "basic";

    private static final int WAITING_DAYS = 30;

    private static final double ANNUAL_RATE = 0.01;

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
        return timeDeposit.getBalance() * ANNUAL_RATE / 12;
    }
}
