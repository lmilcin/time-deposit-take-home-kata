package org.ikigaidigital;

import org.ikigaidigital.domain.AccruingTimeDeposit;
import org.ikigaidigital.domain.interest.BasicPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.InterestAccrualStrategies;
import org.ikigaidigital.domain.interest.PremiumPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.StudentPlanAccrualStrategy;
import org.ikigaidigital.domain.interest.UnknownPlanTypeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * On {@link #ACCOUNTING_DATE}, a deposit with 59 days (made 2026-01-10) reaches its first monthly anniversary
 * for the 30-day plans (accrual starts 2026-02-09), and one with 74 days for premium (accrual starts 2026-02-09).
 */
class TimeDepositCalculatorTest {

    private static final LocalDate ACCOUNTING_DATE = LocalDate.of(2026, 3, 9);

    private final TimeDepositCalculator calculator = new TimeDepositCalculator(
            new InterestAccrualStrategies(List.of(
                    new BasicPlanAccrualStrategy(), new StudentPlanAccrualStrategy(), new PremiumPlanAccrualStrategy())),
            ACCOUNTING_DATE);

    @Test
    void appliesRoundedMonthlyInterestOfEachPlanWhenDue() {
        List<AccruingTimeDeposit> deposits = List.of(
                deposit(1, "basic", 1234567.00, 59, null),
                deposit(2, "student", 1000.00, 59, null),
                deposit(3, "premium", 1000.00, 74, null));

        calculator.updateBalance(List.copyOf(deposits));

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsExactly(1235595.81, 1002.50, 1004.17);
        assertThat(deposits).extracting(AccruingTimeDeposit::getLastInterestAppliedOn)
                .containsOnly(ACCOUNTING_DATE);
    }

    @Test
    void leavesDepositsUnchangedWhenInterestIsNotDue() {
        List<AccruingTimeDeposit> deposits = List.of(
                deposit(1, "basic", 1000.00, 58, null),                // anniversary is tomorrow
                deposit(2, "premium", 1000.00, 59, null),              // premium accrual has not started yet
                deposit(3, "basic", 1000.00, 59, ACCOUNTING_DATE));    // already calculated for this date

        calculator.updateBalance(List.copyOf(deposits));

        assertThat(deposits).extracting(TimeDeposit::getBalance).containsOnly(1000.00);
        assertThat(deposits).extracting(AccruingTimeDeposit::getLastInterestAppliedOn)
                .containsExactly(null, null, ACCOUNTING_DATE);
    }

    @Test
    void recordsTheCalculationEvenWhenInterestIsZero() {
        AccruingTimeDeposit olderThanAYear = deposit(1, "student", 1000.00, 424, LocalDate.of(2026, 2, 9));

        calculator.updateBalance(List.of(olderThanAYear));

        assertThat(olderThanAYear.getBalance()).isEqualTo(1000.00);
        assertThat(olderThanAYear.getLastInterestAppliedOn()).isEqualTo(ACCOUNTING_DATE);
    }

    @Test
    void treatsPlainTimeDepositAsNeverHavingInterestApplied() {
        TimeDeposit plain = new TimeDeposit(1, "basic", 1000.00, 59);

        calculator.updateBalance(List.of(plain));

        assertThat(plain.getBalance()).isEqualTo(1000.83);
    }

    @Test
    void unknownPlanTypeFailsBeforeAnyDepositIsChanged() {
        AccruingTimeDeposit due = deposit(1, "basic", 1000.00, 59, null);

        assertThatThrownBy(() -> calculator.updateBalance(List.of(due, deposit(2, "gold", 1000.00, 59, null))))
                .isInstanceOf(UnknownPlanTypeException.class)
                .hasMessageContaining("gold");

        assertThat(due.getBalance()).isEqualTo(1000.00);
        assertThat(due.getLastInterestAppliedOn()).isNull();
    }

    private static AccruingTimeDeposit deposit(int id, String planType, double balance, int days, LocalDate lastApplied) {
        return new AccruingTimeDeposit(id, planType, balance, days, lastApplied);
    }
}
