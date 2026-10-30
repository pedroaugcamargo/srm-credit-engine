package com.srmasset.creditengine.domain.pricing.strategy;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.srmasset.creditengine.domain.pricing.PricingContext;
import com.srmasset.creditengine.domain.pricing.PricingStrategy;
import com.srmasset.creditengine.domain.pricing.ReceivableType;

@Component
public final class ChequeStrategy implements PricingStrategy {

	private static final BigDecimal MONTHLY_SPREAD = new BigDecimal("0.025");

	@Override
	public ReceivableType type() {
		return ReceivableType.CHEQUE;
	}

	@Override
	public BigDecimal monthlySpread(PricingContext context) {
		Objects.requireNonNull(context, "context");
		return MONTHLY_SPREAD;
	}

}
