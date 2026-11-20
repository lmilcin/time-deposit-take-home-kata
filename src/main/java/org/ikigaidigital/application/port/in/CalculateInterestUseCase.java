package org.ikigaidigital.application.port.in;

import java.time.LocalDate;

public interface CalculateInterestUseCase {

    /**
     * Runs the daily interest batch: applies monthly interest to every time deposit for which it is due
     * on the given accounting date, and advances {@code days} of every time deposit by one.
     * Runs must be for consecutive accounting dates; a run that did not complete is resumed by calling this
     * again with the same accounting date.
     *
     * @param accountingDate the date the batch runs for; it may differ from the current calendar date
     *                       (e.g. a run after midnight, in another time zone, or delayed by an outage)
     * @throws org.ikigaidigital.domain.interest.UnknownPlanTypeException if a time deposit has an unknown plan type
     * @throws org.ikigaidigital.domain.process.InterestCalculationNotAllowedException if the accounting date is
     *         neither the day after the latest completed run nor the date of a pending run
     * @throws org.ikigaidigital.domain.process.InterestCalculationConflictException if another run progressed
     *         concurrently
     */
    void calculateInterest(LocalDate accountingDate);
}
