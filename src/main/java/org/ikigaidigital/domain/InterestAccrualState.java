package org.ikigaidigital.domain;

import org.ikigaidigital.TimeDeposit;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link TimeDeposit} together with the state needed to decide whether monthly interest is due.
 * The shared {@link TimeDeposit} class is intentionally left unchanged to avoid breaking changes.
 *
 * @param lastInterestAppliedOn accounting date of the last monthly interest calculation, or {@code null}
 *                              if it has never run for this deposit
 */
public record InterestAccrualState(TimeDeposit timeDeposit, LocalDate lastInterestAppliedOn) {

    public InterestAccrualState {
        Objects.requireNonNull(timeDeposit, "timeDeposit");
    }

    public Optional<LocalDate> lastInterestApplied() {
        return Optional.ofNullable(lastInterestAppliedOn);
    }
}
