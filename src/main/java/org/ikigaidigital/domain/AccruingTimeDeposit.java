package org.ikigaidigital.domain;

import org.ikigaidigital.TimeDeposit;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A {@link TimeDeposit} that also tracks when its monthly interest calculation last ran.
 * Extending (rather than changing) the shared {@link TimeDeposit} class avoids breaking changes, and lets these
 * deposits pass through {@code TimeDepositCalculator.updateBalance(List<TimeDeposit>)} unchanged in signature.
 */
public class AccruingTimeDeposit extends TimeDeposit {

    private LocalDate lastInterestAppliedOn;

    /**
     * @param lastInterestAppliedOn accounting date of the last monthly interest calculation, or {@code null}
     *                              if it has never run for this deposit
     */
    public AccruingTimeDeposit(int id, String planType, Double balance, int days, LocalDate lastInterestAppliedOn) {
        super(id, planType, balance, days);
        this.lastInterestAppliedOn = lastInterestAppliedOn;
    }

    /** Accounting date of the last monthly interest calculation, or {@code null} if it has never run. */
    public LocalDate getLastInterestAppliedOn() {
        return lastInterestAppliedOn;
    }

    public Optional<LocalDate> lastInterestApplied() {
        return Optional.ofNullable(lastInterestAppliedOn);
    }

    public void setLastInterestAppliedOn(LocalDate lastInterestAppliedOn) {
        this.lastInterestAppliedOn = lastInterestAppliedOn;
    }
}
