package org.ikigaidigital.domain.process;

import java.time.LocalDate;
import java.util.Objects;

/**
 * State of the latest interest calculation run, used to make sure runs happen once per accounting date, in
 * order, and that an interrupted run is resumed instead of repeated.
 *
 * @param accountingDate         accounting date of the latest run
 * @param lastProcessedDepositId id of the last time deposit whose batch was committed by that run, or
 *                               {@link #NO_DEPOSIT_PROCESSED} if none was
 * @param status                 whether the latest run has completed
 */
public record InterestCalculationProcess(LocalDate accountingDate,
                                         int lastProcessedDepositId,
                                         InterestCalculationStatus status) {

    /** Below every time deposit id, so processing after it starts with the first deposit. */
    public static final int NO_DEPOSIT_PROCESSED = -1;

    public InterestCalculationProcess {
        Objects.requireNonNull(accountingDate, "accountingDate");
        Objects.requireNonNull(status, "status");
    }

    /** A new run may start only for the day after a completed run, so no accounting date is skipped or repeated. */
    public boolean canStartRunFor(LocalDate requestedDate) {
        return status == InterestCalculationStatus.COMPLETED && requestedDate.equals(accountingDate.plusDays(1));
    }

    /** A run that has not completed may only be resumed, with its own accounting date. */
    public boolean canResumeRunFor(LocalDate requestedDate) {
        return status == InterestCalculationStatus.PENDING && requestedDate.equals(accountingDate);
    }
}
