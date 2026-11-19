package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.CalculateInterestUseCase;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.TimeDepositDetails;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

public class TimeDepositService implements GetTimeDepositsUseCase, CalculateInterestUseCase {

    private final TimeDepositRepository repository;
    private final TimeDepositCalculator calculator;
    private final int batchSize;

    public TimeDepositService(TimeDepositRepository repository, TimeDepositCalculator calculator, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        this.repository = repository;
        this.calculator = calculator;
        this.batchSize = batchSize;
    }

    @Override
    public Stream<TimeDepositDetails> getTimeDeposits(OptionalInt afterId) {
        return repository.findAllWithWithdrawalsInBatches(afterId, batchSize).flatMap(List::stream);
    }

    /**
     * Applies {@link TimeDepositCalculator#updateBalance} to each batch and then advances {@code days}
     * by one for every deposit that passed through it, since each execution accounts for one day.
     * Interest is therefore calculated using {@code days} as it was before the run.
     * Each batch is calculated and persisted independently. Recovery from an interrupted run
     * (some batches already updated) is not handled yet.
     */
    @Override
    public void calculateInterest() {
        try (Stream<List<TimeDeposit>> batches = repository.findAllInBatches(batchSize)) {
            batches.forEach(batch -> {
                calculator.updateBalance(batch);
                repository.updateBalancesAndDays(batch.stream().map(TimeDepositService::advanceOneDay).toList());
            });
        }
    }

    /** {@link TimeDeposit} has no setter for days and must not be changed, so a new instance is created. */
    private static TimeDeposit advanceOneDay(TimeDeposit timeDeposit) {
        return new TimeDeposit(timeDeposit.getId(), timeDeposit.getPlanType(), timeDeposit.getBalance(),
                timeDeposit.getDays() + 1);
    }
}
