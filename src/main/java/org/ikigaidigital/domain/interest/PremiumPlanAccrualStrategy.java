package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.AccruingTimeDeposit;

import java.time.LocalDate;

/** Premium plan: 5% a year, paid monthly; no accrual for the first 45 days, then due every calendar month. */
public final class PremiumPlanAccrualStrategy implements InterestAccrualStrategy {

    public static final String PLAN_TYPE = "premium";

    private static final int WAITING_DAYS = 45;

    private static final double ANNUAL_RATE = 0.05;

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
