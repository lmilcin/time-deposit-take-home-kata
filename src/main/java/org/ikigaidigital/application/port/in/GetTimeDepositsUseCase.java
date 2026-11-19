package org.ikigaidigital.application.port.in;

import org.ikigaidigital.domain.TimeDepositDetails;

import java.util.OptionalInt;
import java.util.stream.Stream;

public interface GetTimeDepositsUseCase {

    /**
     * Returns time deposits with their withdrawals as a lazily evaluated stream, so that
     * arbitrarily many deposits can be consumed with bounded memory.
     * Results are ordered by id ascending; this ordering is part of the contract, as it allows
     * clients to resume an interrupted fetch by passing the last received id as {@code afterId}.
     * The caller is responsible for closing the stream.
     *
     * @param afterId if present, only time deposits with an id greater than this value are returned
     */
    Stream<TimeDepositDetails> getTimeDeposits(OptionalInt afterId);
}
