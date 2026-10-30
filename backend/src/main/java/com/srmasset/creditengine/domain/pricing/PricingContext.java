package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record PricingContext(BigDecimal faceValue, BigDecimal termMonths) {

	public PricingContext {
		Objects.requireNonNull(faceValue, "faceValue");
		Objects.requireNonNull(termMonths, "termMonths");
	}

}
