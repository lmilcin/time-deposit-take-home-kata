package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.CalculateInterestUseCase;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

public class TimeDepositService implements GetTimeDepositsUseCase, CalculateInterestUseCase {

    private final TimeDepositRepository repository;
    private final InterestAccrualStrategies accrualStrategies;
    private final int batchSize;

    public TimeDepositService(TimeDepositRepository repository,
                              InterestAccrualStrategies accrualStrategies,
                              int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        this.repository = repository;
        this.accrualStrategies = accrualStrategies;
        this.batchSize = batchSize;
    }

    @Override
    public Stream<TimeDepositDetails> getTimeDeposits(OptionalInt afterId) {
        return repository.findAllWithWithdrawalsInBatches(afterId, batchSize).flatMap(List::stream);
    }

    /**
     * Each batch is calculated and persisted independently. Recovery from an interrupted run
     * (some batches already updated) is not handled yet.
     */
    @Override
    public void calculateInterest(LocalDate accountingDate) {
        TimeDepositCalculator calculator = new TimeDepositCalculator(accrualStrategies, accountingDate);
        try (Stream<List<AccruingTimeDeposit>> batches = repository.findAllForInterestCalculationInBatches(batchSize)) {
            batches.forEach(batch -> repository.saveInterestCalculation(processBatch(batch, calculator)));
        }
    }

    /**
     * The whole batch passes through {@link TimeDepositCalculator#updateBalance}, which decides per deposit
     * whether interest is due and records when it ran. Every deposit then advances {@code days} by one, so
     * interest uses {@code days} as it was before the run.
     */
    private static List<AccruingTimeDeposit> processBatch(List<AccruingTimeDeposit> batch,
                                                          TimeDepositCalculator calculator) {
        calculator.updateBalance(List.<TimeDeposit>copyOf(batch));
        return batch.stream().map(TimeDepositService::advanceOneDay).toList();
    }

    /** {@link TimeDeposit} has no setter for days and must not be changed, so a new instance is created. */
    private static AccruingTimeDeposit advanceOneDay(AccruingTimeDeposit timeDeposit) {
        return new AccruingTimeDeposit(timeDeposit.getId(), timeDeposit.getPlanType(), timeDeposit.getBalance(),
                timeDeposit.getDays() + 1, timeDeposit.getLastInterestAppliedOn());
    }
}
