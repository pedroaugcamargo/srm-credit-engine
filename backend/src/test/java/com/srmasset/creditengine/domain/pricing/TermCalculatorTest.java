package com.srmasset.creditengine.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TermCalculatorTest {

	private final TermCalculator calculator = new TermCalculator();

	@Test
	void ninetyActualDaysAreThreeMonths() {
		BigDecimal term = calculator.termMonths(LocalDate.of(2026, 10, 26), LocalDate.of(2027, 1, 24));

		assertEquals(0, term.compareTo(new BigDecimal("3")));
	}

	@Test
	void fortyFiveActualDaysAreOneAndAHalfMonths() {
		BigDecimal term = calculator.termMonths(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 12, 10));

		assertEquals(0, term.compareTo(new BigDecimal("1.5")));
	}

	@Test
	void rejectsDueDateOnTheOperationDate() {
		LocalDate operationDate = LocalDate.of(2026, 10, 26);

		assertThrows(InvalidTermException.class, () -> calculator.termMonths(operationDate, operationDate));
	}

	@Test
	void rejectsDueDateBeforeTheOperationDate() {
		assertThrows(InvalidTermException.class,
				() -> calculator.termMonths(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 25)));
	}

}
