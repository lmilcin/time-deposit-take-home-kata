package org.ikigaidigital.adapter.out.persistence;

import org.ikigaidigital.TestcontainersConfiguration;
import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.Withdrawal;
import org.ikigaidigital.domain.process.InterestCalculationConflictException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the Flyway seed data (6 time deposits, 4 withdrawals, process row for 2026-03-08 completed);
 * each test is rolled back.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JdbcTimeDepositRepository.class, JdbcInterestCalculationProcessRepository.class})
class JdbcTimeDepositRepositoryTest {

    private static final LocalDate ACCOUNTING_DATE = LocalDate.of(2026, 3, 9);

    @Autowired
    JdbcTimeDepositRepository repository;

    @Autowired
    JdbcInterestCalculationProcessRepository processRepository;

    @Test
    void fetchesAllTimeDepositsInOrderedBatches() {
        try (Stream<List<AccruingTimeDeposit>> batches = repository.findAllForInterestCalculationInBatches(-1, 4)) {
            assertThat(batches.map(batch -> batch.stream().map(TimeDeposit::getId).toList()))
                    .containsExactly(List.of(1, 2, 3, 4), List.of(5, 6));
        }
    }

    @Test
    void stopsWhenLastBatchIsExactlyFull() {
        try (Stream<List<AccruingTimeDeposit>> batches = repository.findAllForInterestCalculationInBatches(-1, 3)) {
            assertThat(batches.map(List::size)).containsExactly(3, 3);
        }
    }

    @Test
    void fetchesForInterestCalculationAfterGivenId() {
        try (Stream<List<AccruingTimeDeposit>> batches = repository.findAllForInterestCalculationInBatches(2, 3)) {
            assertThat(batches.map(batch -> batch.stream().map(TimeDeposit::getId).toList()))
                    .containsExactly(List.of(3, 4, 5), List.of(6));
        }
    }

    @Test
    void mapsTimeDepositColumns() {
        try (Stream<List<AccruingTimeDeposit>> batches = repository.findAllForInterestCalculationInBatches(-1, 1)) {
            AccruingTimeDeposit first = batches.findFirst().orElseThrow().get(0);
            assertThat(first.getLastInterestAppliedOn()).isNull();
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
    void savesBalanceDaysAndLastInterestAppliedOnAndAdvancesTheRun() {
        processRepository.start(LocalDate.of(2026, 3, 8), ACCOUNTING_DATE);

        repository.saveInterestCalculation(ACCOUNTING_DATE, -1, List.of(
                new AccruingTimeDeposit(2, "basic", 25020.83, 61, ACCOUNTING_DATE),
                new AccruingTimeDeposit(5, "premium", 50000.00, 41, null)));

        List<AccruingTimeDeposit> all = findAll();
        assertThat(all).extracting(TimeDeposit::getBalance)
                .containsExactly(10000.00, 25020.83, 5000.00, 7500.00, 50000.00, 100000.00);
        assertThat(all).extracting(TimeDeposit::getDays)
                .containsExactly(15, 61, 120, 400, 41, 90);
        assertThat(all).extracting(AccruingTimeDeposit::getLastInterestAppliedOn)
                .containsExactly(null, ACCOUNTING_DATE, null, null, null, null);
        assertThat(processRepository.find().lastProcessedDepositId()).isEqualTo(5);
    }

    @Test
    void savesNothingWhenTheRunIsNotAtTheExpectedDeposit() {
        processRepository.start(LocalDate.of(2026, 3, 8), ACCOUNTING_DATE);

        assertThatThrownBy(() -> repository.saveInterestCalculation(ACCOUNTING_DATE, 3, List.of(
                new AccruingTimeDeposit(4, "student", 9999.99, 401, ACCOUNTING_DATE))))
                .isInstanceOf(InterestCalculationConflictException.class);

        assertThat(findAll().get(3).getBalance()).isEqualTo(7500.00);
        assertThat(processRepository.find().lastProcessedDepositId()).isEqualTo(-1);
    }

    private List<AccruingTimeDeposit> findAll() {
        try (Stream<List<AccruingTimeDeposit>> batches = repository.findAllForInterestCalculationInBatches(-1, 10)) {
            return batches.flatMap(List::stream).toList();
        }
    }

    private static List<Withdrawal> withdrawalsOf(List<TimeDepositDetails> all, int timeDepositId) {
        return all.stream()
                .filter(details -> details.timeDeposit().getId() == timeDepositId)
                .findFirst().orElseThrow()
                .withdrawals();
    }
}
