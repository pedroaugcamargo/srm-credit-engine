package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;

public interface PricingStrategy {

	ReceivableType type();

	BigDecimal monthlySpread(PricingContext context);

}
