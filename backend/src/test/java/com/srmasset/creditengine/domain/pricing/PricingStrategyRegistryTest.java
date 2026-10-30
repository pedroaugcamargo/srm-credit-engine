package com.srmasset.creditengine.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.srmasset.creditengine.domain.pricing.strategy.ChequeStrategy;
import com.srmasset.creditengine.domain.pricing.strategy.DuplicataMercantilStrategy;

class PricingStrategyRegistryTest {

	private final PricingContext context = new PricingContext(new BigDecimal("100000.00"), new BigDecimal("3"));

	@Test
	void resolvesTheSpreadOfEachReceivableType() {
		PricingStrategyRegistry registry = new PricingStrategyRegistry(List.of(
				new DuplicataMercantilStrategy(),
				new ChequeStrategy()));

		assertEquals(new BigDecimal("0.015"), registry.monthlySpread(ReceivableType.DUPLICATA_MERCANTIL, context));
		assertEquals(new BigDecimal("0.025"), registry.monthlySpread(ReceivableType.CHEQUE, context));
	}

	@Test
	void rejectsAReceivableTypeWithoutStrategy() {
		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> new PricingStrategyRegistry(List.of(new DuplicataMercantilStrategy())));

		assertTrue(error.getMessage().contains("CHEQUE"));
	}

	@Test
	void rejectsTwoStrategiesForTheSameReceivableType() {
		PricingStrategy extraDuplicata = new PricingStrategy() {
			@Override
			public ReceivableType type() {
				return ReceivableType.DUPLICATA_MERCANTIL;
			}

			@Override
			public BigDecimal monthlySpread(PricingContext context) {
				return new BigDecimal("0.020");
			}
		};

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> new PricingStrategyRegistry(List.of(
						new DuplicataMercantilStrategy(),
						extraDuplicata,
						new ChequeStrategy())));

		assertTrue(error.getMessage().contains("DUPLICATA_MERCANTIL"));
	}

}
