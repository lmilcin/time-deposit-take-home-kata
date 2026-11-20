package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.port.out.InterestCalculationProcessRepository;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.interest.BasicPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;
import org.ikigaidigital.domain.interest.PremiumPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.StudentPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.UnknownPlanTypeException;
import org.ikigaidigital.domain.process.InterestCalculationConflictException;
import org.ikigaidigital.domain.process.InterestCalculationNotAllowedException;
import org.ikigaidigital.domain.process.InterestCalculationProcess;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.ikigaidigital.domain.process.InterestCalculationStatus.COMPLETED;
import static org.ikigaidigital.domain.process.InterestCalculationStatus.PENDING;

class TimeDepositServiceTest {

    private static final LocalDate ACCOUNTING_DATE = LocalDate.of(2026, 3, 9);
    private static final LocalDate PREVIOUS_DAY = ACCOUNTING_DATE.minusDays(1);
    private static final LocalDate PREVIOUS_MONTH = LocalDate.of(2026, 2, 9);
    private static final int BATCH_SIZE = 2;

    private static final InterestAccrualStrategies STRATEGIES = new InterestAccrualStrategies(List.of(
            new BasicPlanAccrualStrategy(), new StudentPlanAccrualStrategy(), new PremiumPlanAccrualStrategy()));

    /** Interest due on {@link #ACCOUNTING_DATE} for deposits 1, 4 and 6 (see comments). */
    private final InMemoryDatabase database = new InMemoryDatabase(List.of(
            deposit(1, "basic", 1000.00, 59, null),              // made 2026-01-10, first due 2026-03-09
            deposit(2, "basic", 2000.00, 40, null),              // accrual starts 2026-02-28, first due 2026-03-28
            deposit(3, "premium", 3000.00, 59, null),            // accrual starts 2026-02-24, first due 2026-03-24
            deposit(4, "premium", 4000.00, 74, null),            // accrual starts 2026-02-09, first due 2026-03-09
            deposit(5, "student", 5000.00, 59, ACCOUNTING_DATE), // already calculated for this accounting date
            deposit(6, "student", 6000.00, 424, PREVIOUS_MONTH)  // older than a year: due, but no interest
    ));

    private final TimeDepositService service = serviceFor(database);

    @Test
    void appliesInterestOnlyToDepositsDueOnTheAccountingDate() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(database.copyOfAll()).extracting(TimeDeposit::getBalance)
                .containsExactly(1000.83, 2000.00, 3000.00, 4016.67, 5000.00, 6000.00);
    }

    @Test
    void recordsAccountingDateForDepositsWhoseMonthlyCalculationRan() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(database.copyOfAll()).extracting(AccruingTimeDeposit::getLastInterestAppliedOn)
                .containsExactly(ACCOUNTING_DATE, null, null, ACCOUNTING_DATE, ACCOUNTING_DATE, ACCOUNTING_DATE);
    }

    @Test
    void advancesDaysOfEveryDepositByOne() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(database.copyOfAll()).extracting(TimeDeposit::getDays)
                .containsExactly(60, 41, 60, 75, 60, 425);
    }

    @Test
    void persistsEveryBatch() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(database.savedBatchSizes).containsExactly(2, 2, 2);
    }

    @Test
    void completesTheRunAtTheLastDeposit() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(database.process).isEqualTo(new InterestCalculationProcess(ACCOUNTING_DATE, 6, COMPLETED));
    }

    @Test
    void completesARunWithoutTimeDeposits() {
        InMemoryDatabase empty = new InMemoryDatabase(List.of());

        serviceFor(empty).calculateInterest(ACCOUNTING_DATE);

        assertThat(empty.process).isEqualTo(new InterestCalculationProcess(ACCOUNTING_DATE, -1, COMPLETED));
    }

    @Test
    void rejectsRepeatingACompletedAccountingDateWithoutChangingAnything() {
        service.calculateInterest(ACCOUNTING_DATE);
        List<AccruingTimeDeposit> afterFirstRun = database.copyOfAll();

        assertThatThrownBy(() -> service.calculateInterest(ACCOUNTING_DATE))
                .isInstanceOf(InterestCalculationNotAllowedException.class);

        assertThat(database.copyOfAll()).usingRecursiveFieldByFieldElementComparator().isEqualTo(afterFirstRun);
        assertThat(database.process).isEqualTo(new InterestCalculationProcess(ACCOUNTING_DATE, 6, COMPLETED));
    }

    @Test
    void failedRunStaysPendingAfterItsLastCommittedBatch() {
        InMemoryDatabase withUnknownPlan = new InMemoryDatabase(List.of(
                deposit(1, "basic", 1000.00, 59, null),
                deposit(2, "basic", 2000.00, 40, null),
                deposit(3, "basic", 3000.00, 59, null),
                deposit(4, "gold", 4000.00, 59, null)));

        assertThatThrownBy(() -> serviceFor(withUnknownPlan).calculateInterest(ACCOUNTING_DATE))
                .isInstanceOf(UnknownPlanTypeException.class)
                .hasMessageContaining("gold");

        assertThat(withUnknownPlan.savedBatchSizes).containsExactly(2);
        assertThat(withUnknownPlan.process).isEqualTo(new InterestCalculationProcess(ACCOUNTING_DATE, 2, PENDING));
        AccruingTimeDeposit dueInFailedBatch = withUnknownPlan.copyOfAll().get(2);
        assertThat(dueInFailedBatch.getBalance()).isEqualTo(3000.00);
        assertThat(dueInFailedBatch.getDays()).isEqualTo(59);
    }

    @Test
    void resumingAFailedRunProcessesEveryDepositExactlyOnce() {
        InMemoryDatabase withUnknownPlan = new InMemoryDatabase(List.of(
                deposit(1, "basic", 1000.00, 59, null),
                deposit(2, "basic", 2000.00, 40, null),
                deposit(3, "basic", 3000.00, 59, null),
                deposit(4, "gold", 4000.00, 59, null)));
        TimeDepositService failing = serviceFor(withUnknownPlan);
        assertThatThrownBy(() -> failing.calculateInterest(ACCOUNTING_DATE))
                .isInstanceOf(UnknownPlanTypeException.class);
        withUnknownPlan.replace(deposit(4, "basic", 4000.00, 59, null));

        failing.calculateInterest(ACCOUNTING_DATE);

        assertThat(withUnknownPlan.copyOfAll()).extracting(TimeDeposit::getBalance)
                .containsExactly(1000.83, 2000.00, 3002.50, 4003.33);
        assertThat(withUnknownPlan.copyOfAll()).extracting(TimeDeposit::getDays)
                .containsExactly(60, 41, 60, 60);
        assertThat(withUnknownPlan.process).isEqualTo(new InterestCalculationProcess(ACCOUNTING_DATE, 4, COMPLETED));
    }

    @Test
    void stopsWithoutSavingWhenAnotherRunProgressedConcurrently() {
        // after this run commits its first batch, another run commits deposits 3 and 4
        database.afterSave = () -> database.process = new InterestCalculationProcess(ACCOUNTING_DATE, 4, PENDING);

        assertThatThrownBy(() -> service.calculateInterest(ACCOUNTING_DATE))
                .isInstanceOf(InterestCalculationConflictException.class);

        assertThat(database.savedBatchSizes).containsExactly(2);
        assertThat(database.copyOfAll()).extracting(TimeDeposit::getDays)
                .containsExactly(60, 41, 59, 74, 59, 424);
    }

    @Test
    void getTimeDepositsFlattensAllBatches() {
        try (Stream<TimeDepositDetails> deposits = service.getTimeDeposits(OptionalInt.empty())) {
            assertThat(deposits.map(details -> details.timeDeposit().getId())).containsExactly(1, 2, 3, 4, 5, 6);
        }
    }

    @Test
    void getTimeDepositsResumesAfterGivenId() {
        try (Stream<TimeDepositDetails> deposits = service.getTimeDeposits(OptionalInt.of(2))) {
            assertThat(deposits.map(details -> details.timeDeposit().getId())).containsExactly(3, 4, 5, 6);
        }
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> new TimeDepositService(database, database, STRATEGIES, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TimeDepositService serviceFor(InMemoryDatabase database) {
        return new TimeDepositService(database, database, STRATEGIES, BATCH_SIZE);
    }

    private static AccruingTimeDeposit deposit(int id, String planType, double balance, int days, LocalDate lastApplied) {
        return new AccruingTimeDeposit(id, planType, balance, days, lastApplied);
    }

    /**
     * Time deposits and the process record, updated with the same conditions as the JDBC adapters. Starts with a
     * completed run for the day before {@link #ACCOUNTING_DATE}.
     */
    private static final class InMemoryDatabase implements TimeDepositRepository, InterestCalculationProcessRepository {

        private final List<AccruingTimeDeposit> store;
        private final List<Integer> savedBatchSizes = new ArrayList<>();
        private InterestCalculationProcess process =
                new InterestCalculationProcess(PREVIOUS_DAY, InterestCalculationProcess.NO_DEPOSIT_PROCESSED, COMPLETED);
        private Runnable afterSave = () -> { };

        InMemoryDatabase(List<AccruingTimeDeposit> deposits) {
            this.store = new ArrayList<>(deposits);
        }

        List<AccruingTimeDeposit> copyOfAll() {
            return store.stream().map(InMemoryDatabase::copy).toList();
        }

        void replace(AccruingTimeDeposit updated) {
            store.replaceAll(existing -> existing.getId() == updated.getId() ? copy(updated) : existing);
        }

        @Override
        public Stream<List<AccruingTimeDeposit>> findAllForInterestCalculationInBatches(int afterId, int batchSize) {
            List<AccruingTimeDeposit> remaining = store.stream().filter(td -> td.getId() > afterId).toList();
            return IntStream.iterate(0, from -> from < remaining.size(), from -> from + batchSize)
                    .mapToObj(from -> remaining.subList(from, Math.min(from + batchSize, remaining.size())).stream()
                            .map(InMemoryDatabase::copy)
                            .toList());
        }

        @Override
        public Stream<List<TimeDepositDetails>> findAllWithWithdrawalsInBatches(OptionalInt afterId, int batchSize) {
            return findAllForInterestCalculationInBatches(afterId.orElse(Integer.MIN_VALUE), batchSize)
                    .map(batch -> batch.stream().map(td -> new TimeDepositDetails(td, List.of())).toList());
        }

        @Override
        public void saveInterestCalculation(LocalDate accountingDate, int previousLastProcessedDepositId,
                                            List<AccruingTimeDeposit> batch) {
            requireProcess(accountingDate, previousLastProcessedDepositId);
            process = new InterestCalculationProcess(accountingDate, batch.get(batch.size() - 1).getId(), process.status());
            savedBatchSizes.add(batch.size());
            batch.forEach(this::replace);
            afterSave.run();
        }

        @Override
        public InterestCalculationProcess find() {
            return process;
        }

        @Override
        public void start(LocalDate previousAccountingDate, LocalDate newAccountingDate) {
            if (!process.accountingDate().equals(previousAccountingDate)) {
                throw new InterestCalculationConflictException("latest run is not for " + previousAccountingDate);
            }
            process = new InterestCalculationProcess(newAccountingDate, InterestCalculationProcess.NO_DEPOSIT_PROCESSED, PENDING);
        }

        @Override
        public void complete(LocalDate accountingDate, int lastProcessedDepositId) {
            requireProcess(accountingDate, lastProcessedDepositId);
            process = new InterestCalculationProcess(accountingDate, lastProcessedDepositId, COMPLETED);
        }

        private void requireProcess(LocalDate accountingDate, int lastProcessedDepositId) {
            if (!process.accountingDate().equals(accountingDate) || process.lastProcessedDepositId() != lastProcessedDepositId) {
                throw new InterestCalculationConflictException("run is not at deposit " + lastProcessedDepositId);
            }
        }

        private static AccruingTimeDeposit copy(AccruingTimeDeposit td) {
            return new AccruingTimeDeposit(td.getId(), td.getPlanType(), td.getBalance(), td.getDays(),
                    td.getLastInterestAppliedOn());
        }
    }
}
