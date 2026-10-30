package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ch.obermuhlner.math.big.BigDecimalMath;

@Component
public final class PricingEngine {

	private static final MathContext MATH = MathContext.DECIMAL128;

	private final PricingStrategyRegistry strategies;
	private final RoundingPolicy roundingPolicy;
	private final BigDecimal baseRate;
	private final FxRateProvider fxRateProvider;

	public PricingEngine(
			PricingStrategyRegistry strategies,
			RoundingPolicy roundingPolicy,
			@Value("${credit-engine.pricing.base-rate}") BigDecimal baseRate,
			FxRateProvider fxRateProvider) {
		this.strategies = Objects.requireNonNull(strategies, "strategies");
		this.roundingPolicy = Objects.requireNonNull(roundingPolicy, "roundingPolicy");
		this.baseRate = Objects.requireNonNull(baseRate, "baseRate");
		this.fxRateProvider = Objects.requireNonNull(fxRateProvider, "fxRateProvider");
	}

	public PricingResult price(PricingInput input) {
		Objects.requireNonNull(input, "input");
		PricingContext context = new PricingContext(input.faceValue(), input.termMonths());
		BigDecimal spread = strategies.monthlySpread(input.receivableType(), context);
		BigDecimal monthlyRate = baseRate.add(spread, MATH);
		BigDecimal factor = compoundFactor(BigDecimal.ONE.add(monthlyRate, MATH), input.termMonths());
		BigDecimal presentValueBrl = roundingPolicy.round(input.faceValue().divide(factor, MATH));
		BigDecimal discountBrl = input.faceValue().subtract(presentValueBrl);

		Settlement settlement = switch (input.paymentCurrency()) {
			case BRL -> new Settlement(null, presentValueBrl);
			case USD -> {
				BigDecimal fxRate = Objects.requireNonNull(fxRateProvider.brlPerUsd(), "fxRate");
				yield new Settlement(fxRate, roundingPolicy.round(presentValueBrl.divide(fxRate, MATH)));
			}
		};

		return new PricingResult(
				input.faceValue(),
				input.termMonths(),
				baseRate,
				spread,
				presentValueBrl,
				discountBrl,
				input.paymentCurrency(),
				settlement.fxRate(),
				settlement.netAmount());
	}

	private record Settlement(BigDecimal fxRate, BigDecimal netAmount) {
	}

	private static BigDecimal compoundFactor(BigDecimal base, BigDecimal termMonths) {
		if (termMonths.stripTrailingZeros().scale() <= 0) {
			return base.pow(termMonths.intValueExact(), MATH);
		}
		return BigDecimalMath.pow(base, termMonths, MATH);
	}

}
