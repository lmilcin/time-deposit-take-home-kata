package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.CalculateInterestUseCase;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.InterestAccrualState;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimeDepositService implements GetTimeDepositsUseCase, CalculateInterestUseCase {

    private final TimeDepositRepository repository;
    private final TimeDepositCalculator calculator;
    private final InterestAccrualStrategies accrualStrategies;
    private final int batchSize;

    public TimeDepositService(TimeDepositRepository repository,
                              TimeDepositCalculator calculator,
                              InterestAccrualStrategies accrualStrategies,
                              int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        this.repository = repository;
        this.calculator = calculator;
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
        try (Stream<List<InterestAccrualState>> batches = repository.findAllForInterestCalculationInBatches(batchSize)) {
            batches.forEach(batch -> repository.saveInterestCalculation(processBatch(batch, accountingDate)));
        }
    }

    /**
     * Only deposits whose plan strategy says interest is due pass through
     * {@link TimeDepositCalculator#updateBalance}; their {@code lastInterestAppliedOn} becomes the accounting
     * date, even if the calculated interest is zero, as it records when the monthly calculation ran.
     * Every deposit then advances {@code days} by one, so interest uses {@code days} as it was before the run.
     * Strategies are resolved for the whole batch first, so an unknown plan type fails the batch before
     * anything is calculated or persisted.
     */
    private List<InterestAccrualState> processBatch(List<InterestAccrualState> batch, LocalDate accountingDate) {
        List<TimeDeposit> due = batch.stream()
                .filter(state -> accrualStrategies.isDue(state, accountingDate))
                .map(InterestAccrualState::timeDeposit)
                .toList();
        calculator.updateBalance(due);

        Set<Integer> dueIds = due.stream().map(TimeDeposit::getId).collect(Collectors.toSet());
        return batch.stream()
                .map(state -> new InterestAccrualState(
                        advanceOneDay(state.timeDeposit()),
                        dueIds.contains(state.timeDeposit().getId()) ? accountingDate : state.lastInterestAppliedOn()))
                .toList();
    }

    /** {@link TimeDeposit} has no setter for days and must not be changed, so a new instance is created. */
    private static TimeDeposit advanceOneDay(TimeDeposit timeDeposit) {
        return new TimeDeposit(timeDeposit.getId(), timeDeposit.getPlanType(), timeDeposit.getBalance(),
                timeDeposit.getDays() + 1);
    }
}
