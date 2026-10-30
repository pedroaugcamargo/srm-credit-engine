package com.srmasset.creditengine.domain.pricing.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.srmasset.creditengine.domain.pricing.PricingContext;

class ReceivablePricingStrategyTest {

	private final PricingContext context = new PricingContext(new BigDecimal("100000.00"), new BigDecimal("3"));

	@Test
	void duplicataMonthlySpreadIs0_015() {
		assertEquals(new BigDecimal("0.015"), new DuplicataMercantilStrategy().monthlySpread(context));
	}

	@Test
	void chequeMonthlySpreadIs0_025() {
		assertEquals(new BigDecimal("0.025"), new ChequeStrategy().monthlySpread(context));
	}

}
