package org.ikigaidigital.application.port.out;

import org.ikigaidigital.domain.process.InterestCalculationProcess;

import java.time.LocalDate;

/**
 * Access to the single interest calculation process record. Each update is conditional on the record being in
 * the expected state (optimistic lock).
 *
 * @see TimeDepositRepository#saveInterestCalculation(LocalDate, int, java.util.List) for advancing a run per batch
 */
public interface InterestCalculationProcessRepository {

    InterestCalculationProcess find();

    /**
     * Starts a run for {@code newAccountingDate}: pending, with no deposit processed.
     *
     * @throws org.ikigaidigital.domain.process.InterestCalculationConflictException if the latest run is no longer
     *                                                                               for {@code previousAccountingDate}
     */
    void start(LocalDate previousAccountingDate, LocalDate newAccountingDate);

    /**
     * Marks the run for {@code accountingDate} as completed.
     *
     * @param lastProcessedDepositId the last processed deposit id this run committed
     * @throws org.ikigaidigital.domain.process.InterestCalculationConflictException if another run progressed
     */
    void complete(LocalDate accountingDate, int lastProcessedDepositId);
}
