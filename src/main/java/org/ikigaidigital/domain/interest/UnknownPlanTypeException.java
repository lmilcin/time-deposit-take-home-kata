package org.ikigaidigital.domain.interest;

/** Thrown when a time deposit has a plan type no {@link InterestAccrualStrategy} is defined for. */
public class UnknownPlanTypeException extends RuntimeException {

    public UnknownPlanTypeException(String planType, int timeDepositId) {
        super("Unknown plan type '" + planType + "' of time deposit " + timeDepositId);
    }
}
