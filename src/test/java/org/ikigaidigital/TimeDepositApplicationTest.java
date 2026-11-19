package org.ikigaidigital;

import org.ikigaidigital.domain.interest.UnknownPlanTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimeDepositApplicationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void servesOpenApiContract() throws Exception {
        mockMvc.perform(get("/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/time-deposits/calculate-interest")));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void returnsAllTimeDepositsWithWithdrawals() throws Exception {
        mockMvc.perform(get("/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].planType").value("basic"))
                .andExpect(jsonPath("$[0].days").value(15))
                .andExpect(jsonPath("$[0].balance").isNumber())
                .andExpect(jsonPath("$[0].withdrawals", hasSize(0)))
                .andExpect(jsonPath("$[2].withdrawals", hasSize(2)))
                .andExpect(jsonPath("$[2].withdrawals[0].amount").value(250.00))
                .andExpect(jsonPath("$[2].withdrawals[0].date").value("2026-10-01"));
    }

    @Test
    void returnsTimeDepositsOrderedById() throws Exception {
        mockMvc.perform(get("/time-deposits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(1, 2, 3, 4, 5, 6)));
    }

    @Test
    void resumesFetchingAfterGivenId() throws Exception {
        mockMvc.perform(get("/time-deposits").param("afterId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains(4, 5, 6)));
    }

    @Test
    void calculateInterestRequiresAccountingDate() throws Exception {
        mockMvc.perform(post("/time-deposits/calculate-interest")).andExpect(status().isBadRequest());
    }

    /**
     * With the seed data and accounting date 2026-03-09, the monthly calculation is due for deposits
     * 2, 3, 4 (student older than a year, so no interest is added) and 6, but not for 1 and 5, which are
     * still in their initial no-accrual period.
     * Rolled back so that the modified rows do not leak into other tests.
     */
    @Test
    @Transactional
    void calculatesMonthlyInterestForDueDepositsAndAdvancesDaysOfAll() throws Exception {
        List<TimeDeposit> before = loadTimeDeposits();
        List<TimeDeposit> expected = loadTimeDeposits();
        new TimeDepositCalculator().updateBalance(expected.stream().filter(td -> DUE_IDS.contains(td.getId())).toList());

        mockMvc.perform(post("/time-deposits/calculate-interest").param("accounting_date", "2026-03-09"))
                .andExpect(status().isNoContent());

        List<TimeDeposit> after = loadTimeDeposits();
        assertThat(after)
                .extracting(TimeDeposit::getBalance)
                .containsExactlyElementsOf(expected.stream().map(TimeDeposit::getBalance).toList())
                .containsExactly(10000.00, 25020.83, 5012.50, 7500.00, 50000.00, 100416.67);
        assertThat(after)
                .extracting(TimeDeposit::getDays)
                .containsExactlyElementsOf(before.stream().map(td -> td.getDays() + 1).toList());
        LocalDate accountingDate = LocalDate.of(2026, 3, 9);
        assertThat(loadLastInterestAppliedOn())
                .containsExactly(null, accountingDate, accountingDate, accountingDate, null, accountingDate);
    }

    @Test
    @Transactional
    void failsInterestCalculationForUnknownPlanTypeWithoutUpdatingDeposits() {
        jdbcTemplate.update("INSERT INTO time_deposits (plan_type, days, balance) VALUES ('gold', 60, 1000.00)");
        List<TimeDeposit> before = loadTimeDeposits();

        assertThatThrownBy(() -> mockMvc.perform(
                post("/time-deposits/calculate-interest").param("accounting_date", "2026-03-09")))
                .hasRootCauseInstanceOf(UnknownPlanTypeException.class);

        assertThat(loadTimeDeposits()).usingRecursiveFieldByFieldElementComparator().isEqualTo(before);
    }

    private static final List<Integer> DUE_IDS = List.of(2, 3, 4, 6);

    private List<TimeDeposit> loadTimeDeposits() {
        return jdbcTemplate.query("SELECT id, plan_type, balance, days FROM time_deposits ORDER BY id",
                (rs, rowNum) -> new TimeDeposit(rs.getInt("id"), rs.getString("plan_type"),
                        rs.getBigDecimal("balance").doubleValue(), rs.getInt("days")));
    }

    private List<LocalDate> loadLastInterestAppliedOn() {
        return jdbcTemplate.query("SELECT last_interest_applied_on FROM time_deposits ORDER BY id",
                (rs, rowNum) -> rs.getDate(1) == null ? null : rs.getDate(1).toLocalDate());
    }
}
