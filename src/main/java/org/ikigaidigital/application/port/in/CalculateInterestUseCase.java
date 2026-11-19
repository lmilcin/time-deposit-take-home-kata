package org.ikigaidigital.application.port.in;

import java.time.LocalDate;

public interface CalculateInterestUseCase {

    /**
     * Runs the daily interest batch: applies monthly interest to every time deposit for which it is due
     * on the given accounting date, and advances {@code days} of every time deposit by one.
     *
     * @param accountingDate the date the batch runs for; it may differ from the current calendar date
     *                       (e.g. a run after midnight, in another time zone, or delayed by an outage)
     * @throws org.ikigaidigital.domain.interest.UnknownPlanTypeException if a time deposit has an unknown plan type
     */
    void calculateInterest(LocalDate accountingDate);
}
