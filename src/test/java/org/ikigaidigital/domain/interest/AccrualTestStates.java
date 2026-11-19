package org.ikigaidigital.domain.interest;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.InterestAccrualState;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class AccrualTestStates {

    private AccrualTestStates() {
    }

    /** A deposit made on {@code depositDate}, with {@code days} as of {@code accountingDate} (day of deposit = 1). */
    static InterestAccrualState state(String planType, LocalDate depositDate, LocalDate accountingDate,
                                      LocalDate lastInterestAppliedOn) {
        int days = (int) ChronoUnit.DAYS.between(depositDate, accountingDate) + 1;
        return new InterestAccrualState(new TimeDeposit(1, planType, 1000.00, days), lastInterestAppliedOn);
    }
}
