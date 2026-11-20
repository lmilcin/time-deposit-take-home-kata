package org.ikigaidigital.adapter.out.persistence;

import org.ikigaidigital.application.port.out.InterestCalculationProcessRepository;
import org.ikigaidigital.domain.process.InterestCalculationConflictException;
import org.ikigaidigital.domain.process.InterestCalculationProcess;
import org.ikigaidigital.domain.process.InterestCalculationStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Implements the optimistic lock on the single {@code interest_calculation_process} row described in
 * ARCHITECTURE.md: every update is conditional and must update exactly one row.
 */
@Repository
public class JdbcInterestCalculationProcessRepository implements InterestCalculationProcessRepository {

    private static final String SELECT = """
            SELECT accounting_date, last_processed_deposit_id, last_status
            FROM interest_calculation_process
            """;

    private static final String START = """
            UPDATE interest_calculation_process
            SET accounting_date = :newAccountingDate,
                last_status = 'pending',
                last_processed_deposit_id = :noDepositProcessed,
                updated = NOW()
            WHERE accounting_date = :previousAccountingDate
            """;

    private static final String ADVANCE = """
            UPDATE interest_calculation_process
            SET last_processed_deposit_id = :lastProcessedDepositId,
                updated = NOW()
            WHERE accounting_date = :accountingDate
              AND last_processed_deposit_id = :previousLastProcessedDepositId
            """;

    private static final String COMPLETE = """
            UPDATE interest_calculation_process
            SET last_status = 'completed',
                updated = NOW()
            WHERE accounting_date = :accountingDate
              AND last_processed_deposit_id = :lastProcessedDepositId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcInterestCalculationProcessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InterestCalculationProcess find() {
        return jdbc.queryForObject(SELECT, new MapSqlParameterSource(), (rs, rowNum) -> new InterestCalculationProcess(
                rs.getObject("accounting_date", LocalDate.class),
                rs.getInt("last_processed_deposit_id"),
                InterestCalculationStatus.valueOf(rs.getString("last_status").toUpperCase(Locale.ROOT))));
    }

    @Override
    public void start(LocalDate previousAccountingDate, LocalDate newAccountingDate) {
        int updated = jdbc.update(START, new MapSqlParameterSource()
                .addValue("newAccountingDate", newAccountingDate)
                .addValue("noDepositProcessed", InterestCalculationProcess.NO_DEPOSIT_PROCESSED)
                .addValue("previousAccountingDate", previousAccountingDate));
        requireSingleRow(updated, "Could not start interest calculation for " + newAccountingDate
                + ": the latest run is no longer for " + previousAccountingDate);
    }

    /**
     * Advances a pending run by one batch. Must be called in the transaction that persists the batch, before the
     * deposits are updated: the update locks the row, so a concurrent run waits and then fails its own check.
     */
    void advance(LocalDate accountingDate, int previousLastProcessedDepositId, int lastProcessedDepositId) {
        int updated = jdbc.update(ADVANCE, new MapSqlParameterSource()
                .addValue("lastProcessedDepositId", lastProcessedDepositId)
                .addValue("accountingDate", accountingDate)
                .addValue("previousLastProcessedDepositId", previousLastProcessedDepositId));
        requireSingleRow(updated, "Interest calculation for " + accountingDate + " is no longer at deposit "
                + previousLastProcessedDepositId + "; another run may have progressed concurrently");
    }

    @Override
    public void complete(LocalDate accountingDate, int lastProcessedDepositId) {
        int updated = jdbc.update(COMPLETE, new MapSqlParameterSource()
                .addValue("accountingDate", accountingDate)
                .addValue("lastProcessedDepositId", lastProcessedDepositId));
        requireSingleRow(updated, "Could not complete interest calculation for " + accountingDate
                + " at deposit " + lastProcessedDepositId + "; another run may have progressed concurrently");
    }

    private static void requireSingleRow(int updated, String message) {
        if (updated != 1) {
            throw new InterestCalculationConflictException(message);
        }
    }
}
