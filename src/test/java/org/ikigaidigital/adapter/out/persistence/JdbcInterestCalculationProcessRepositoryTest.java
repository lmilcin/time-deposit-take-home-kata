package org.ikigaidigital.adapter.out.persistence;

import org.ikigaidigital.TestcontainersConfiguration;
import org.ikigaidigital.domain.process.InterestCalculationConflictException;
import org.ikigaidigital.domain.process.InterestCalculationProcess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.ikigaidigital.domain.process.InterestCalculationStatus.COMPLETED;
import static org.ikigaidigital.domain.process.InterestCalculationStatus.PENDING;

/** Runs against the Flyway seed row (2026-03-08, completed, no deposit processed); each test is rolled back. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JdbcInterestCalculationProcessRepository.class})
class JdbcInterestCalculationProcessRepositoryTest {

    private static final LocalDate SEEDED = LocalDate.of(2026, 3, 8);
    private static final LocalDate NEXT = LocalDate.of(2026, 3, 9);

    @Autowired
    JdbcInterestCalculationProcessRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void findsTheSeededCompletedRun() {
        assertThat(repository.find()).isEqualTo(new InterestCalculationProcess(SEEDED, -1, COMPLETED));
    }

    @Test
    void startsAPendingRunWithNoDepositProcessed() {
        repository.start(SEEDED, NEXT);

        assertThat(repository.find()).isEqualTo(new InterestCalculationProcess(NEXT, -1, PENDING));
    }

    @Test
    void startFailsWhenTheLatestRunIsForAnotherDate() {
        assertThatThrownBy(() -> repository.start(SEEDED.minusDays(1), NEXT))
                .isInstanceOf(InterestCalculationConflictException.class);

        assertThat(repository.find()).isEqualTo(new InterestCalculationProcess(SEEDED, -1, COMPLETED));
    }

    @Test
    void advancesOnlyFromTheExpectedLastProcessedDeposit() {
        repository.start(SEEDED, NEXT);
        repository.advance(NEXT, -1, 3);

        assertThatThrownBy(() -> repository.advance(NEXT, -1, 6))
                .isInstanceOf(InterestCalculationConflictException.class);
        assertThat(repository.find()).isEqualTo(new InterestCalculationProcess(NEXT, 3, PENDING));
    }

    @Test
    void completesTheRunAtItsLastProcessedDeposit() {
        repository.start(SEEDED, NEXT);
        repository.advance(NEXT, -1, 6);

        repository.complete(NEXT, 6);

        assertThat(repository.find()).isEqualTo(new InterestCalculationProcess(NEXT, 6, COMPLETED));
    }

    @Test
    void completeFailsWhenAnotherRunProgressed() {
        repository.start(SEEDED, NEXT);
        repository.advance(NEXT, -1, 3);

        assertThatThrownBy(() -> repository.complete(NEXT, -1))
                .isInstanceOf(InterestCalculationConflictException.class);
        assertThat(repository.find().status()).isEqualTo(PENDING);
    }

    @Test
    void allowsAtMostOneRow() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO interest_calculation_process (accounting_date, last_processed_deposit_id, last_status, updated)
                VALUES ('2026-03-09', -1, 'completed', NOW())"""))
                .hasMessageContaining("interest_calculation_process_single_row");
    }
}
