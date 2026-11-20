package org.ikigaidigital.domain.process;

/**
 * Thrown when the interest calculation process record was not in the expected state when updating it, i.e.
 * another run started, progressed or completed concurrently. The current step is not committed.
 */
public class InterestCalculationConflictException extends RuntimeException {

    public InterestCalculationConflictException(String message) {
        super(message);
    }
}
