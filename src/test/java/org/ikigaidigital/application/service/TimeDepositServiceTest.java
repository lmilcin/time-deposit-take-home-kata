package org.ikigaidigital.application.service;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositCalculator;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.InterestAccrualState;
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

    private final TimeDepositService service = new TimeDepositService(repository, new TimeDepositCalculator(), STRATEGIES, 2);

    @Test
    void appliesInterestOnlyToDepositsDueOnTheAccountingDate() {
        List<TimeDeposit> expectedDue = repository.copyOfAll().stream()
                .map(InterestAccrualState::timeDeposit)
                .filter(td -> List.of(1, 4, 6).contains(td.getId()))
                .toList();
        new TimeDepositCalculator().updateBalance(expectedDue);

        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(balancesById()).containsExactly(
                expectedDue.get(0).getBalance(), 2000.00, 3000.00, expectedDue.get(1).getBalance(), 5000.00, 6000.00);
        assertThat(expectedDue.get(0).getBalance()).isEqualTo(1000.83);
        assertThat(expectedDue.get(1).getBalance()).isEqualTo(4016.67);
    }

    @Test
    void recordsAccountingDateForDepositsWhoseMonthlyCalculationRan() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(repository.copyOfAll()).extracting(InterestAccrualState::lastInterestAppliedOn)
                .containsExactly(ACCOUNTING_DATE, null, null, ACCOUNTING_DATE, ACCOUNTING_DATE, ACCOUNTING_DATE);
    }

    @Test
    void advancesDaysOfEveryDepositByOne() {
        service.calculateInterest(ACCOUNTING_DATE);

        assertThat(repository.copyOfAll()).extracting(state -> state.timeDeposit().getDays())
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
        TimeDepositService failing = new TimeDepositService(withUnknownPlan, new TimeDepositCalculator(), STRATEGIES, 2);

        assertThatThrownBy(() -> failing.calculateInterest(ACCOUNTING_DATE))
                .isInstanceOf(UnknownPlanTypeException.class)
                .hasMessageContaining("gold");

        assertThat(withUnknownPlan.savedBatchSizes).containsExactly(2);
        TimeDeposit dueInFailedBatch = withUnknownPlan.copyOfAll().get(2).timeDeposit();
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
        assertThatThrownBy(() -> new TimeDepositService(repository, new TimeDepositCalculator(), STRATEGIES, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Double> balancesById() {
        return repository.copyOfAll().stream().map(state -> state.timeDeposit().getBalance()).toList();
    }

    private static InterestAccrualState state(int id, String planType, double balance, int days, LocalDate lastApplied) {
        return new InterestAccrualState(new TimeDeposit(id, planType, balance, days), lastApplied);
    }

    private static final class InMemoryRepository implements TimeDepositRepository {

        private final List<InterestAccrualState> store;
        private final List<Integer> savedBatchSizes = new ArrayList<>();

        InMemoryRepository(List<InterestAccrualState> states) {
            this.store = new ArrayList<>(states);
        }

        List<InterestAccrualState> copyOfAll() {
            return store.stream().map(InMemoryRepository::copy).toList();
        }

        @Override
        public Stream<List<InterestAccrualState>> findAllForInterestCalculationInBatches(int batchSize) {
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
                            .map(InterestAccrualState::timeDeposit)
                            .filter(td -> td.getId() > startAfterId)
                            .map(td -> new TimeDepositDetails(td, List.of()))
                            .toList())
                    .filter(batch -> !batch.isEmpty());
        }

        @Override
        public void saveInterestCalculation(List<InterestAccrualState> states) {
            savedBatchSizes.add(states.size());
            states.forEach(updated -> store.replaceAll(existing ->
                    existing.timeDeposit().getId() == updated.timeDeposit().getId() ? copy(updated) : existing));
        }

        private static InterestAccrualState copy(InterestAccrualState state) {
            TimeDeposit td = state.timeDeposit();
            return new InterestAccrualState(
                    new TimeDeposit(td.getId(), td.getPlanType(), td.getBalance(), td.getDays()),
                    state.lastInterestAppliedOn());
        }
    }
}
