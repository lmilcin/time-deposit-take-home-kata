package org.ikigaidigital.domain.process;

/** Status of the latest interest calculation run. */
public enum InterestCalculationStatus {
    /** The run has started and not all time deposits have been processed yet. */
    PENDING,
    /** All time deposits have been processed for the accounting date. */
    COMPLETED
}
