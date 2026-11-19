package org.ikigaidigital.application.port.out;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

public interface TimeDepositRepository {

    /**
     * Lazily fetches all time deposits ordered by id, one batch at a time.
     * Only the current batch is held in memory. The caller is responsible for closing the stream.
     */
    Stream<List<TimeDeposit>> findAllInBatches(int batchSize);

    /**
     * Lazily fetches time deposits with their withdrawals, one batch at a time.
     * Results must be ordered by id ascending, both within and across batches; callers rely on it
     * to resume fetching after the last received id.
     * Only the current batch is held in memory. The caller is responsible for closing the stream.
     * Results are not guaranteed to be consistent when interest is being calculated concurrently.
     *
     * @param afterId if present, only time deposits with an id greater than this value are returned
     */
    Stream<List<TimeDepositDetails>> findAllWithWithdrawalsInBatches(OptionalInt afterId, int batchSize);

    /** Persists the balances and days of the given time deposits as a single batch. */
    void updateBalancesAndDays(List<TimeDeposit> timeDeposits);
}
