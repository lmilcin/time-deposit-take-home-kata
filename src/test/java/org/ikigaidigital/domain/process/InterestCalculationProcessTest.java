package org.ikigaidigital.domain.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ikigaidigital.domain.process.InterestCalculationStatus.COMPLETED;
import static org.ikigaidigital.domain.process.InterestCalculationStatus.PENDING;

class InterestCalculationProcessTest {

    private static final LocalDate LATEST = LocalDate.of(2026, 3, 9);

    private final InterestCalculationProcess completed = new InterestCalculationProcess(LATEST, 6, COMPLETED);
    private final InterestCalculationProcess pending = new InterestCalculationProcess(LATEST, 2, PENDING);

    @Test
    void completedRunAllowsStartingOnlyTheNextDay() {
        assertThat(completed.canStartRunFor(LATEST.plusDays(1))).isTrue();
        assertThat(completed.canResumeRunFor(LATEST.plusDays(1))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 2})
    void completedRunRejectsRepeatedSkippedAndEarlierDates(int daysAfterLatest) {
        LocalDate requested = LATEST.plusDays(daysAfterLatest);
        assertThat(completed.canStartRunFor(requested)).isFalse();
        assertThat(completed.canResumeRunFor(requested)).isFalse();
    }

    @Test
    void pendingRunAllowsOnlyResumingTheSameDate() {
        assertThat(pending.canResumeRunFor(LATEST)).isTrue();
        assertThat(pending.canStartRunFor(LATEST)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void pendingRunRejectsOtherDates(int daysAfterLatest) {
        LocalDate requested = LATEST.plusDays(daysAfterLatest);
        assertThat(pending.canStartRunFor(requested)).isFalse();
        assertThat(pending.canResumeRunFor(requested)).isFalse();
    }

    @Test
    void rejectionExplainsWhichDateIsExpected() {
        assertThat(new InterestCalculationNotAllowedException(completed, LATEST.plusDays(2)))
                .hasMessageContaining("2026-03-11")
                .hasMessageContaining("next run must be for 2026-03-10");
        assertThat(new InterestCalculationNotAllowedException(pending, LATEST.plusDays(1)))
                .hasMessageContaining("pending")
                .hasMessageContaining("resumed with the same accounting date");
    }
}
