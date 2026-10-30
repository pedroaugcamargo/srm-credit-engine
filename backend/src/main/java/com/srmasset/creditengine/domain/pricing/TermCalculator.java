package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public final class TermCalculator {

	private static final BigDecimal DAYS_IN_MONTH = new BigDecimal("30");
	private static final MathContext MATH = MathContext.DECIMAL128;

	public BigDecimal termMonths(LocalDate operationDate, LocalDate dueDate) {
		Objects.requireNonNull(operationDate, "operationDate");
		Objects.requireNonNull(dueDate, "dueDate");
		long days = ChronoUnit.DAYS.between(operationDate, dueDate);
		if (days <= 0) {
			throw new InvalidTermException("Due date must be after the operation date");
		}
		return new BigDecimal(Long.toString(days)).divide(DAYS_IN_MONTH, MATH);
	}

}
