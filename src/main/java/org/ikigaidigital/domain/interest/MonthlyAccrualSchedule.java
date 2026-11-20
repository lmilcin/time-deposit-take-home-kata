package org.ikigaidigital.domain.interest;

import org.ikigaidigital.domain.AccruingTimeDeposit;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Monthly accrual schedule shared by the plan strategies.
 * <p>
 * Interest is not accrued during the first {@code waitingDays} days of the deposit; accrual starts on day
 * {@code waitingDays + 1}. Interest is then due every calendar month, on the monthly anniversaries of the
 * accrual start. Anniversaries are computed from the accrual start (not from the previous anniversary), so
 * month-end dates do not drift (e.g. Jan 31 -> Feb 28 -> Mar 31).
 * <p>
 * Dates are derived from the batch accounting date rather than the system clock. {@code days} is the number
 * of days the deposit has existed, counting the day it was made (starting at 1), and advances by one for each
 * accounting date, so the deposit was made on {@code accountingDate - (days - 1)}.
 * <p>
 * Interest is due when the latest anniversary on or before the accounting date is after the last time it was
 * applied. This makes re-running the same accounting date a no-op and catches up a single missed anniversary.
 */
final class MonthlyAccrualSchedule {

    private MonthlyAccrualSchedule() {
    }

    static boolean isDue(AccruingTimeDeposit timeDeposit, LocalDate accountingDate, int waitingDays) {
        LocalDate depositDate = accountingDate.minusDays(timeDeposit.getDays() - 1L);
        LocalDate accrualStart = depositDate.plusDays(waitingDays);
        Optional<LocalDate> latestAnniversary = latestAnniversaryOnOrBefore(accrualStart, accountingDate);
        return latestAnniversary.isPresent()
                && timeDeposit.lastInterestApplied().map(latestAnniversary.get()::isAfter).orElse(true);
    }

    private static Optional<LocalDate> latestAnniversaryOnOrBefore(LocalDate accrualStart, LocalDate date) {
        long months = Math.max(0, ChronoUnit.MONTHS.between(accrualStart, date));
        // MONTHS.between may undercount around month ends (e.g. Jan 31 -> Feb 28), so step forward as needed.
        while (!accrualStart.plusMonths(months + 1).isAfter(date)) {
            months++;
        }
        return months == 0 ? Optional.empty() : Optional.of(accrualStart.plusMonths(months));
    }
}
