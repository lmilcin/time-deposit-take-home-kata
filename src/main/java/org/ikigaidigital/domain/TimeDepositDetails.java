package org.ikigaidigital.domain;

import org.ikigaidigital.TimeDeposit;

import java.util.List;

/**
 * Read model combining a {@link TimeDeposit} with its withdrawals.
 * The shared {@link TimeDeposit} class is intentionally left unchanged to avoid breaking changes.
 */
public record TimeDepositDetails(TimeDeposit timeDeposit, List<Withdrawal> withdrawals) {

    public TimeDepositDetails {
        withdrawals = List.copyOf(withdrawals);
    }
}
