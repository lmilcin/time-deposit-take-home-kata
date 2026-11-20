package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.AccruingTimeDeposit;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class AccrualTestStates {

    private AccrualTestStates() {
    }

    /** A deposit made on {@code depositDate}, with {@code days} as of {@code accountingDate} (day of deposit = 1). */
    static AccruingTimeDeposit state(String planType, LocalDate depositDate, LocalDate accountingDate,
                                     LocalDate lastInterestAppliedOn) {
        int days = (int) ChronoUnit.DAYS.between(depositDate, accountingDate) + 1;
        return new AccruingTimeDeposit(1, planType, 1000.00, days, lastInterestAppliedOn);
    }
}
