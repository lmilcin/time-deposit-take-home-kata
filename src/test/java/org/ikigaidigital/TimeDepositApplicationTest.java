package org.ikigaidigital;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Rolled back so that the modified balances and days do not leak into other tests. */
    @Test
    @Transactional
    void calculatesInterestForAllTimeDepositsLikeTheCalculatorAndAdvancesDays() throws Exception {
        List<TimeDeposit> before = loadTimeDeposits();
        List<TimeDeposit> expected = loadTimeDeposits();
        new TimeDepositCalculator().updateBalance(expected);

        mockMvc.perform(post("/time-deposits/calculate-interest")).andExpect(status().isNoContent());

        List<TimeDeposit> after = loadTimeDeposits();
        assertThat(after)
                .extracting(TimeDeposit::getBalance)
                .containsExactlyElementsOf(expected.stream().map(TimeDeposit::getBalance).toList());
        assertThat(after)
                .extracting(TimeDeposit::getDays)
                .containsExactlyElementsOf(before.stream().map(td -> td.getDays() + 1).toList());
    }

    private List<TimeDeposit> loadTimeDeposits() {
        return jdbcTemplate.query("SELECT id, plan_type, balance, days FROM time_deposits ORDER BY id",
                (rs, rowNum) -> new TimeDeposit(rs.getInt("id"), rs.getString("plan_type"),
                        rs.getBigDecimal("balance").doubleValue(), rs.getInt("days")));
    }
}
