package com.srmasset.creditengine.infra;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.srmasset.creditengine.domain.pricing.FxRateProvider;

@Component
public final class FixedFxRateProvider implements FxRateProvider {

	private final BigDecimal brlPerUsd;

	public FixedFxRateProvider(@Value("${credit-engine.fx.brl-per-usd}") BigDecimal brlPerUsd) {
		this.brlPerUsd = Objects.requireNonNull(brlPerUsd, "brlPerUsd");
	}

	@Override
	public BigDecimal brlPerUsd() {
		return brlPerUsd;
	}

}
