package com.srmasset.creditengine.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.RoundingMode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RoundingPolicyBindingTest {

	private final RoundingPolicy policy;

	@Autowired
	RoundingPolicyBindingTest(RoundingPolicy policy) {
		this.policy = policy;
	}

	@Test
	void defaultsToScaleTwoAndHalfEven() {
		assertEquals(2, policy.scale());
		assertEquals(RoundingMode.HALF_EVEN, policy.mode());
	}

}
