package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.interest.BasicPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;
import org.ikigaidigital.domain.interest.PremiumPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.StudentPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.UnknownPlanTypeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeDepositServiceTest {

    private static final LocalDate ACCOUNTING_DATE = LocalDate.of(2026, 3, 9);
    private static final LocalDate PREVIOUS_MONTH = LocalDate.of(2026, 2, 9);

    private static final InterestAccrualStrategies STRATEGIES = new InterestAccrualStrategies(List.of(
            new BasicPlanAccrualStrategy(), new StudentPlanAccrualStrategy(), new PremiumPlanAccrualStrategy()));

    /** Interest due on {@link #ACCOUNTING_DATE} for deposits 1, 4 and 6 (see comments). */
    private final InMemoryRepository repository = new InMemoryRepository(List.of(
            state(1, "basic", 1000.00, 59, null),              // made 2026-01-10, first due 2026-03-09
            state(2, "basic", 2000.00, 40, null),              // accrual starts 2026-02-28, first due 2026-03-28
            state(3, "premium", 3000.00, 59, null),            // accrual starts 2026-02-24, first due 2026-03-24
            state(4, "premium", 4000.00, 74, null),            // accrual starts 2026-02-09, first due 2026-03-09
            state(5, "student", 5000.00, 59, ACCOUNTING_DATE), // already calculated for this accounting date
            state(6, "student", 6000.00, 424, PREVIOUS_MONTH)  // older than a year: due, but calculator adds nothing
    ));

    private final TimeDepositService service = new TimeDepositService(repository, STRATEGIES, 2);

    @Test
    void appliesInterestOnlyToDepositsDueOnTheAccountingDate() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(repository.copyOfAll()).extracting(TimeDeposit::getBalance)
                .containsExactly(1000.83, 2000.00, 3000.00, 4016.67, 5000.00, 6000.00);
    }

    @Test
    void recordsAccountingDateForDepositsWhoseMonthlyCalculationRan() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(repository.copyOfAll()).extracting(AccruingTimeDeposit::getLastInterestAppliedOn)
                .containsExactly(ACCOUNTING_DATE, null, null, ACCOUNTING_DATE, ACCOUNTING_DATE, ACCOUNTING_DATE);
    }

    @Test
    void advancesDaysOfEveryDepositByOne() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(repository.copyOfAll()).extracting(TimeDeposit::getDays)
                .containsExactly(60, 41, 60, 75, 60, 425);
    }

    @Test
    void persistsEveryBatch() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(repository.savedBatchSizes).containsExactly(2, 2, 2);
    }

    @Test
    void unknownPlanTypeFailsTheProcessBeforeItsBatchIsCalculatedOrPersisted() {
        InMemoryRepository withUnknownPlan = new InMemoryRepository(List.of(
                state(1, "basic", 1000.00, 59, null),
                state(2, "basic", 2000.00, 40, null),
                state(3, "basic", 3000.00, 59, null),
                state(4, "gold", 4000.00, 59, null)));
        TimeDepositService failing = new TimeDepositService(withUnknownPlan, STRATEGIES, 2);

        assertThatThrownBy(() -> failing.calculateInterest(ACCOUNTING_DATE))
                .isInstanceOf(UnknownPlanTypeException.class)
                .hasMessageContaining("gold");

        assertThat(withUnknownPlan.savedBatchSizes).containsExactly(2);
        TimeDeposit dueInFailedBatch = withUnknownPlan.copyOfAll().get(2);
        assertThat(dueInFailedBatch.getBalance()).isEqualTo(3000.00);
        assertThat(dueInFailedBatch.getDays()).isEqualTo(59);
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
        assertThatThrownBy(() -> new TimeDepositService(repository, STRATEGIES, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AccruingTimeDeposit state(int id, String planType, double balance, int days, LocalDate lastApplied) {
        return new AccruingTimeDeposit(id, planType, balance, days, lastApplied);
    }

    private static final class InMemoryRepository implements TimeDepositRepository {

        private final List<AccruingTimeDeposit> store;
        private final List<Integer> savedBatchSizes = new ArrayList<>();

        InMemoryRepository(List<AccruingTimeDeposit> states) {
            this.store = new ArrayList<>(states);
        }

        List<AccruingTimeDeposit> copyOfAll() {
            return store.stream().map(InMemoryRepository::copy).toList();
        }

        @Override
        public Stream<List<AccruingTimeDeposit>> findAllForInterestCalculationInBatches(int batchSize) {
            return IntStream.iterate(0, from -> from < store.size(), from -> from + batchSize)
                    .mapToObj(from -> store.subList(from, Math.min(from + batchSize, store.size())).stream()
                            .map(InMemoryRepository::copy)
                            .toList());
        }

        @Override
        public Stream<List<TimeDepositDetails>> findAllWithWithdrawalsInBatches(OptionalInt afterId, int batchSize) {
            int startAfterId = afterId.orElse(Integer.MIN_VALUE);
            return findAllForInterestCalculationInBatches(batchSize)
                    .map(batch -> batch.stream()
                            .filter(td -> td.getId() > startAfterId)
                            .map(td -> new TimeDepositDetails(td, List.of()))
                            .toList())
                    .filter(batch -> !batch.isEmpty());
        }

        @Override
        public void saveInterestCalculation(List<AccruingTimeDeposit> timeDeposits) {
            savedBatchSizes.add(timeDeposits.size());
            timeDeposits.forEach(updated -> store.replaceAll(existing ->
                    existing.getId() == updated.getId() ? copy(updated) : existing));
        }

        private static AccruingTimeDeposit copy(AccruingTimeDeposit td) {
            return new AccruingTimeDeposit(td.getId(), td.getPlanType(), td.getBalance(), td.getDays(),
                    td.getLastInterestAppliedOn());
        }
    }
}
