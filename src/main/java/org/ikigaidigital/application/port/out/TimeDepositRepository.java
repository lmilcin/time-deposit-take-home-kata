package org.ikigaidigital.application.port.out;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;

import java.util.List;
import java.util.stream.Stream;

public interface TimeDepositRepository {

    /**
     * Lazily fetches all time deposits ordered by id, one batch at a time.
     * Only the current batch is held in memory. The caller is responsible for closing the stream.
     */
    Stream<List<TimeDeposit>> findAllInBatches(int batchSize);

    /**
     * Lazily fetches all time deposits with their withdrawals ordered by id, one batch at a time.
     * Only the current batch is held in memory. The caller is responsible for closing the stream.
     * Results are not guaranteed to be consistent when interest is being calculated concurrently.
     */
    Stream<List<TimeDepositDetails>> findAllWithWithdrawalsInBatches(int batchSize);

    /** Persists the balances of the given time deposits as a single batch. */
    void updateBalances(List<TimeDeposit> timeDeposits);
}
