package org.ikigaidigital.adapter.out.persistence;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.application.port.out.TimeDepositRepository;
import org.ikigaidigital.domain.InterestAccrualState;
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

    private static final RowMapper<InterestAccrualState> STATE_ROW_MAPPER = (rs, rowNum) -> {
        Date lastInterestAppliedOn = rs.getDate("last_interest_applied_on");
        return new InterestAccrualState(
                new TimeDeposit(
                        rs.getInt("id"),
                        rs.getString("plan_type"),
                        rs.getBigDecimal("balance").doubleValue(),
                        rs.getInt("days")),
                lastInterestAppliedOn == null ? null : lastInterestAppliedOn.toLocalDate());
    };

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTimeDepositRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Stream<List<InterestAccrualState>> findAllForInterestCalculationInBatches(int batchSize) {
        return batchesAfter(OptionalInt.empty(), batchSize);
    }

    @Override
    public Stream<List<TimeDepositDetails>> findAllWithWithdrawalsInBatches(OptionalInt afterId, int batchSize) {
        return batchesAfter(afterId, batchSize)
                .map(batch -> batch.stream().map(InterestAccrualState::timeDeposit).toList())
                .map(this::withWithdrawals);
    }

    private Stream<List<InterestAccrualState>> batchesAfter(OptionalInt afterId, int batchSize) {
        int startAfterId = afterId.orElse(Integer.MIN_VALUE);
        return StreamSupport.stream(
                new KeysetBatchSpliterator<>(startAfterId, batchSize, lastId -> fetchBatch(lastId, batchSize),
                        state -> state.timeDeposit().getId()),
                false);
    }

    @Override
    @Transactional
    public void saveInterestCalculation(List<InterestAccrualState> states) {
        SqlParameterSource[] parameters = states.stream()
                .map(state -> new MapSqlParameterSource()
                        .addValue("id", state.timeDeposit().getId())
                        .addValue("balance", BigDecimal.valueOf(state.timeDeposit().getBalance()))
                        .addValue("days", state.timeDeposit().getDays())
                        .addValue("lastInterestAppliedOn", state.lastInterestAppliedOn(), Types.DATE))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPDATE_INTEREST_CALCULATION, parameters);
    }

    private List<InterestAccrualState> fetchBatch(int lastId, int batchSize) {
        return jdbc.query(SELECT_BATCH,
                new MapSqlParameterSource().addValue("lastId", lastId).addValue("batchSize", batchSize),
                STATE_ROW_MAPPER);
    }

    /**
     * Loads withdrawals for the whole batch with a single query to avoid N+1 queries.
     * Preserves the id order of the given batch.
     */
    private List<TimeDepositDetails> withWithdrawals(List<TimeDeposit> batch) {
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
