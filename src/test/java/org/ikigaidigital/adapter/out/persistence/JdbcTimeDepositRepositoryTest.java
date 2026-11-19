package org.ikigaidigital.adapter.out.persistence;

import org.ikigaidigital.TestcontainersConfiguration;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.InterestAccrualState;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.Withdrawal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the Flyway seed data (6 time deposits, 4 withdrawals); each test is rolled back. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JdbcTimeDepositRepository.class})
class JdbcTimeDepositRepositoryTest {

    @Autowired
    JdbcTimeDepositRepository repository;

    @Test
    void fetchesAllTimeDepositsInOrderedBatches() {
        try (Stream<List<InterestAccrualState>> batches = repository.findAllForInterestCalculationInBatches(4)) {
            assertThat(batches.map(batch -> batch.stream().map(state -> state.timeDeposit().getId()).toList()))
                    .containsExactly(List.of(1, 2, 3, 4), List.of(5, 6));
        }
    }

    @Test
    void stopsWhenLastBatchIsExactlyFull() {
        try (Stream<List<InterestAccrualState>> batches = repository.findAllForInterestCalculationInBatches(3)) {
            assertThat(batches.map(List::size)).containsExactly(3, 3);
        }
    }

    @Test
    void mapsTimeDepositColumns() {
        try (Stream<List<InterestAccrualState>> batches = repository.findAllForInterestCalculationInBatches(1)) {
            InterestAccrualState state = batches.findFirst().orElseThrow().get(0);
            assertThat(state.lastInterestAppliedOn()).isNull();
            TimeDeposit first = state.timeDeposit();
            assertThat(first.getId()).isEqualTo(1);
            assertThat(first.getPlanType()).isEqualTo("basic");
            assertThat(first.getBalance()).isEqualTo(10000.00);
            assertThat(first.getDays()).isEqualTo(15);
        }
    }

    @Test
    void fetchesWithdrawalsForEachTimeDeposit() {
        try (Stream<List<TimeDepositDetails>> batches = repository.findAllWithWithdrawalsInBatches(OptionalInt.empty(), 2)) {
            List<TimeDepositDetails> all = batches.flatMap(List::stream).toList();

            assertThat(all).hasSize(6);
            assertThat(withdrawalsOf(all, 1)).isEmpty();
            assertThat(withdrawalsOf(all, 3)).containsExactly(
                    new Withdrawal(2, new BigDecimal("250.00"), LocalDate.of(2026, 10, 1)),
                    new Withdrawal(3, new BigDecimal("100.00"), LocalDate.of(2026, 10, 20)));
            assertThat(withdrawalsOf(all, 6)).extracting(Withdrawal::id).containsExactly(4);
        }
    }

    @Test
    void resumesFetchingAfterGivenIdInIdOrderAcrossBatches() {
        try (Stream<List<TimeDepositDetails>> batches = repository.findAllWithWithdrawalsInBatches(OptionalInt.of(1), 2)) {
            assertThat(batches.map(batch -> batch.stream().map(details -> details.timeDeposit().getId()).toList()))
                    .containsExactly(List.of(2, 3), List.of(4, 5), List.of(6));
        }
    }

    @Test
    void returnsNothingWhenResumingAfterLastId() {
        try (Stream<List<TimeDepositDetails>> batches = repository.findAllWithWithdrawalsInBatches(OptionalInt.of(6), 2)) {
            assertThat(batches).isEmpty();
        }
    }

    @Test
    void savesBalanceDaysAndLastInterestAppliedOn() {
        LocalDate accountingDate = LocalDate.of(2026, 3, 9);
        repository.saveInterestCalculation(List.of(
                new InterestAccrualState(new TimeDeposit(2, "basic", 25020.83, 61), accountingDate),
                new InterestAccrualState(new TimeDeposit(5, "premium", 50000.00, 41), null)));

        try (Stream<List<InterestAccrualState>> batches = repository.findAllForInterestCalculationInBatches(10)) {
            List<InterestAccrualState> all = batches.flatMap(List::stream).toList();
            assertThat(all).extracting(state -> state.timeDeposit().getBalance())
                    .containsExactly(10000.00, 25020.83, 5000.00, 7500.00, 50000.00, 100000.00);
            assertThat(all).extracting(state -> state.timeDeposit().getDays())
                    .containsExactly(15, 61, 120, 400, 41, 90);
            assertThat(all).extracting(InterestAccrualState::lastInterestAppliedOn)
                    .containsExactly(null, accountingDate, null, null, null, null);
        }
    }

    private static List<Withdrawal> withdrawalsOf(List<TimeDepositDetails> all, int timeDepositId) {
        return all.stream()
                .filter(details -> details.timeDeposit().getId() == timeDepositId)
                .findFirst().orElseThrow()
                .withdrawals();
    }
}
