package org.ikigaidigital.application.port.in;

import org.ikigaidigital.domain.TimeDepositDetails;

import java.util.stream.Stream;

public interface GetTimeDepositsUseCase {

    /**
     * Returns all time deposits with their withdrawals as a lazily evaluated stream, so that
     * arbitrarily many deposits can be consumed with bounded memory.
     * The caller is responsible for closing the stream.
     */
    Stream<TimeDepositDetails> getTimeDeposits();
}
