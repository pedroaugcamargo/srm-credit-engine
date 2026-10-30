package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ch.obermuhlner.math.big.BigDecimalMath;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public final class PricingEngine {

	private static final MathContext MATH = MathContext.DECIMAL128;

	private final PricingStrategyRegistry strategies;
	private final RoundingPolicy roundingPolicy;
	private final BigDecimal baseRate;
	private final FxRateProvider fxRateProvider;
	private final Timer pricingDuration;

	public PricingEngine(PricingStrategyRegistry strategies, RoundingPolicy roundingPolicy,
			@Value("${credit-engine.pricing.base-rate}") BigDecimal baseRate, FxRateProvider fxRateProvider,
			MeterRegistry meterRegistry) {
		this.strategies = Objects.requireNonNull(strategies, "strategies");
		this.roundingPolicy = Objects.requireNonNull(roundingPolicy, "roundingPolicy");
		this.baseRate = Objects.requireNonNull(baseRate, "baseRate");
		this.fxRateProvider = Objects.requireNonNull(fxRateProvider, "fxRateProvider");
		this.pricingDuration = Timer.builder("pricing_engine_duration").publishPercentiles(0.95)
				.register(meterRegistry);
	}

	public PricingResult price(PricingInput input) {
		Objects.requireNonNull(input, "input");
		BigDecimal fxRate = input.paymentCurrency() == PaymentCurrency.USD ? fxRateProvider.brlPerUsd() : null;
		return price(input, baseRate, fxRate);
	}

	public PricingResult price(PricingInput input, BigDecimal resolvedBaseRate, BigDecimal fxRate) {
		return pricingDuration.record(() -> calculate(input, resolvedBaseRate, fxRate));
	}

	private PricingResult calculate(PricingInput input, BigDecimal resolvedBaseRate, BigDecimal fxRate) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(resolvedBaseRate, "baseRate");
		PricingContext context = new PricingContext(input.faceValue(), input.termMonths());
		BigDecimal spread = strategies.monthlySpread(input.receivableType(), context);
		BigDecimal monthlyRate = resolvedBaseRate.add(spread, MATH);
		BigDecimal factor = compoundFactor(BigDecimal.ONE.add(monthlyRate, MATH), input.termMonths());
		BigDecimal presentValueBrl = roundingPolicy.round(input.faceValue().divide(factor, MATH));
		BigDecimal discountBrl = input.faceValue().subtract(presentValueBrl);

		Settlement settlement = switch (input.paymentCurrency()) {
			case BRL -> new Settlement(null, presentValueBrl);
			case USD -> {
				BigDecimal rate = Objects.requireNonNull(fxRate, "fxRate");
				yield new Settlement(rate, roundingPolicy.round(presentValueBrl.divide(rate, MATH)));
			}
		};

		return new PricingResult(input.faceValue(), input.termMonths(), resolvedBaseRate, spread, presentValueBrl,
				discountBrl, input.paymentCurrency(), settlement.fxRate(), settlement.netAmount());
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
