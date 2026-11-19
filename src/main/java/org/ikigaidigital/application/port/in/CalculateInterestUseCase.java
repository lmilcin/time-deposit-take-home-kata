package org.ikigaidigital.application.port.in;

public interface CalculateInterestUseCase {

    /** Applies monthly interest to the balances of all time deposits. */
    void calculateInterest();
}
