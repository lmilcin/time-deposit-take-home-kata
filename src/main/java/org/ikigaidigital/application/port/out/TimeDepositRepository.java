package org.ikigaidigital.application.port.out;

import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

public interface TimeDepositRepository {

    /**
     * Lazily fetches time deposits with an id greater than {@code afterId}, with the date interest was last applied,
     * ordered by id, one batch at a time. Only the current batch is held in memory. The caller is responsible for
     * closing the stream.
     */
    Stream<List<AccruingTimeDeposit>> findAllForInterestCalculationInBatches(int afterId, int batchSize);

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

    /**
     * In a single transaction, advances the pending run for {@code accountingDate} from
     * {@code previousLastProcessedDepositId} to the id of the last deposit in {@code batch}, and persists balance,
     * days and last interest calculation date of the deposits in the batch. Nothing is persisted if the run could
     * not be advanced.
     *
     * @param batch non-empty, ordered by id
     * @throws org.ikigaidigital.domain.process.InterestCalculationConflictException if the run is not pending for
     *         {@code accountingDate} at {@code previousLastProcessedDepositId}, e.g. another run progressed
     */
    void saveInterestCalculation(LocalDate accountingDate, int previousLastProcessedDepositId,
                                 List<AccruingTimeDeposit> batch);
}
