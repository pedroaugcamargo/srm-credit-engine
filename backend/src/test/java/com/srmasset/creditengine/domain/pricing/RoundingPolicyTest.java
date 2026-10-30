package com.srmasset.creditengine.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

class RoundingPolicyTest {

	private final RoundingPolicy policy = new RoundingPolicy(2, RoundingMode.HALF_EVEN);

	@Test
	void rounds2_345To2_34() {
		assertEquals(new BigDecimal("2.34"), policy.round(new BigDecimal("2.345")));
	}

	@Test
	void rounds2_355To2_36() {
		assertEquals(new BigDecimal("2.36"), policy.round(new BigDecimal("2.355")));
	}

}
