package org.ikigaidigital.adapter.out.persistence;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.TimeDepositDetails;
import org.ikigaidigital.domain.Withdrawal;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Repository
public class JdbcTimeDepositRepository implements TimeDepositRepository {

    /** ORDER BY id is required: keyset pagination and resuming via afterId both depend on it. */
    private static final String SELECT_BATCH = """
            SELECT id, plan_type, balance, days, last_interest_applied_on
            FROM time_deposits
            WHERE id > :lastId
            ORDER BY id
            LIMIT :batchSize
            """;

    private static final String SELECT_WITHDRAWALS = """
            SELECT id, time_deposit_id, amount, date
            FROM withdrawals
            WHERE time_deposit_id IN (:timeDepositIds)
            ORDER BY date, id
            """;

    private static final String UPDATE_INTEREST_CALCULATION = """
            UPDATE time_deposits
            SET balance = :balance, days = :days, last_interest_applied_on = :lastInterestAppliedOn
            WHERE id = :id
            """;

    private static final RowMapper<AccruingTimeDeposit> TIME_DEPOSIT_ROW_MAPPER = (rs, rowNum) -> {
        Date lastInterestAppliedOn = rs.getDate("last_interest_applied_on");
        return new AccruingTimeDeposit(
                rs.getInt("id"),
                rs.getString("plan_type"),
                rs.getBigDecimal("balance").doubleValue(),
                rs.getInt("days"),
                lastInterestAppliedOn == null ? null : lastInterestAppliedOn.toLocalDate());
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcInterestCalculationProcessRepository processRepository;

    public JdbcTimeDepositRepository(NamedParameterJdbcTemplate jdbc,
                                     JdbcInterestCalculationProcessRepository processRepository) {
        this.jdbc = jdbc;
        this.processRepository = processRepository;
    }

    @Override
    public Stream<List<AccruingTimeDeposit>> findAllForInterestCalculationInBatches(int afterId, int batchSize) {
        return batchesAfter(OptionalInt.of(afterId), batchSize);
    }

    @Override
    public Stream<List<TimeDepositDetails>> findAllWithWithdrawalsInBatches(OptionalInt afterId, int batchSize) {
        return batchesAfter(afterId, batchSize).map(this::withWithdrawals);
    }

    private Stream<List<AccruingTimeDeposit>> batchesAfter(OptionalInt afterId, int batchSize) {
        int startAfterId = afterId.orElse(Integer.MIN_VALUE);
        return StreamSupport.stream(
                new KeysetBatchSpliterator<>(startAfterId, batchSize, lastId -> fetchBatch(lastId, batchSize),
                        TimeDeposit::getId),
                false);
    }

    /** The process record is advanced first, so a conflicting run fails before any deposit is written. */
    @Override
    @Transactional
    public void saveInterestCalculation(LocalDate accountingDate, int previousLastProcessedDepositId,
                                        List<AccruingTimeDeposit> batch) {
        if (batch.isEmpty()) {
            throw new IllegalArgumentException("batch must not be empty");
        }
        processRepository.advance(accountingDate, previousLastProcessedDepositId, batch.get(batch.size() - 1).getId());
        SqlParameterSource[] parameters = batch.stream()
                .map(timeDeposit -> new MapSqlParameterSource()
                        .addValue("id", timeDeposit.getId())
                        .addValue("balance", BigDecimal.valueOf(timeDeposit.getBalance()))
                        .addValue("days", timeDeposit.getDays())
                        .addValue("lastInterestAppliedOn", timeDeposit.getLastInterestAppliedOn(), Types.DATE))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPDATE_INTEREST_CALCULATION, parameters);
    }

    private List<AccruingTimeDeposit> fetchBatch(int lastId, int batchSize) {
        return jdbc.query(SELECT_BATCH,
                new MapSqlParameterSource().addValue("lastId", lastId).addValue("batchSize", batchSize),
                TIME_DEPOSIT_ROW_MAPPER);
    }

    /**
     * Loads withdrawals for the whole batch with a single query to avoid N+1 queries.
     * Preserves the id order of the given batch.
     */
    private List<TimeDepositDetails> withWithdrawals(List<? extends TimeDeposit> batch) {
        List<Integer> ids = batch.stream().map(TimeDeposit::getId).toList();
        Map<Integer, List<Withdrawal>> withdrawalsByDepositId = jdbc.query(SELECT_WITHDRAWALS,
                        new MapSqlParameterSource("timeDepositIds", ids),
                        (rs, rowNum) -> Map.entry(
                                rs.getInt("time_deposit_id"),
                                new Withdrawal(rs.getInt("id"), rs.getBigDecimal("amount"), rs.getDate("date").toLocalDate())))
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        return batch.stream()
                .map(timeDeposit -> new TimeDepositDetails(timeDeposit,
                        withdrawalsByDepositId.getOrDefault(timeDeposit.getId(), List.of())))
                .toList();
    }
}
