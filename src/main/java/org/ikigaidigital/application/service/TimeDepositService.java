package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.in.CalculateInterestUseCase;
import org.ikigaidigital.application.port.in.GetTimeDepositsUseCase;
import org.ikigaidigital.application.port.out.InterestCalculationProcessRepository;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;
import org.ikigaidigital.domain.process.InterestCalculationNotAllowedException;
import org.ikigaidigital.domain.process.InterestCalculationProcess;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

public class TimeDepositService implements GetTimeDepositsUseCase, CalculateInterestUseCase {

    private final TimeDepositRepository repository;
    private final InterestCalculationProcessRepository processRepository;
    private final InterestAccrualStrategies accrualStrategies;
    private final int batchSize;

    public TimeDepositService(TimeDepositRepository repository,
                              InterestCalculationProcessRepository processRepository,
                              InterestAccrualStrategies accrualStrategies,
                              int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
        }
        this.repository = repository;
        this.processRepository = processRepository;
        this.accrualStrategies = accrualStrategies;
        this.batchSize = batchSize;
    }

    @Override
    public Stream<TimeDepositDetails> getTimeDeposits(OptionalInt afterId) {
        return repository.findAllWithWithdrawalsInBatches(afterId, batchSize).flatMap(List::stream);
    }

    /**
     * Starts the run for the day after the latest completed run, or resumes a pending run for the same date after
     * its last committed batch, so every deposit is processed exactly once per accounting date. Each batch is
     * committed together with the run's progress; the run is completed after the last batch.
     */
    @Override
    public void calculateInterest(LocalDate accountingDate) {
        int lastProcessedDepositId = startOrResume(accountingDate);
        TimeDepositCalculator calculator = new TimeDepositCalculator(accrualStrategies, accountingDate);
        try (Stream<List<AccruingTimeDeposit>> batches =
                     repository.findAllForInterestCalculationInBatches(lastProcessedDepositId, batchSize)) {
            for (Iterator<List<AccruingTimeDeposit>> it = batches.iterator(); it.hasNext(); ) {
                List<AccruingTimeDeposit> processed = processBatch(it.next(), calculator);
                repository.saveInterestCalculation(accountingDate, lastProcessedDepositId, processed);
                lastProcessedDepositId = processed.get(processed.size() - 1).getId();
            }
        }
        processRepository.complete(accountingDate, lastProcessedDepositId);
    }

    /** @return the id after which time deposits still have to be processed for {@code accountingDate} */
    private int startOrResume(LocalDate accountingDate) {
        InterestCalculationProcess latest = processRepository.find();
        if (latest.canResumeRunFor(accountingDate)) {
            return latest.lastProcessedDepositId();
        }
        if (latest.canStartRunFor(accountingDate)) {
            processRepository.start(latest.accountingDate(), accountingDate);
            return InterestCalculationProcess.NO_DEPOSIT_PROCESSED;
        }
        throw new InterestCalculationNotAllowedException(latest, accountingDate);
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
