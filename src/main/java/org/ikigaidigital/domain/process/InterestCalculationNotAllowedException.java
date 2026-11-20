package org.ikigaidigital.domain.process;

import java.time.LocalDate;
import java.util.Locale;

/** Thrown when a run is requested for an accounting date that is neither the next one nor a resumption. */
public class InterestCalculationNotAllowedException extends RuntimeException {

    public InterestCalculationNotAllowedException(InterestCalculationProcess latest, LocalDate requestedDate) {
        super("Interest calculation for " + requestedDate + " is not allowed: the latest run, for "
                + latest.accountingDate() + ", is " + latest.status().name().toLowerCase(Locale.ROOT)
                + (latest.status() == InterestCalculationStatus.COMPLETED
                ? ". The next run must be for " + latest.accountingDate().plusDays(1) + "."
                : ". It must be resumed with the same accounting date."));
    }
}
