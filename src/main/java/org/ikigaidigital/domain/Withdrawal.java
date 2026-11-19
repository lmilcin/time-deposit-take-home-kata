package org.ikigaidigital.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Withdrawal(int id, BigDecimal amount, LocalDate date) {
}
