package com.srmasset.creditengine.persistence;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Component;

import com.srmasset.creditengine.domain.fx.FxProperties;
import com.srmasset.creditengine.domain.fx.FxQuote;
import com.srmasset.creditengine.domain.fx.FxSelection;
import com.srmasset.creditengine.domain.fx.FxValidity;
import com.srmasset.creditengine.domain.pricing.FxRateProvider;

@Component
public final class DbFxRateProvider implements FxRateProvider {

	private final FxRateRepository rates;
	private final Clock clock;
	private final FxValidity validity;

	public DbFxRateProvider(FxRateRepository rates, java.time.Clock clock, FxProperties properties) {
		this.rates = rates;
		this.clock = clock;
		this.validity = new FxValidity(properties.maxAge());
	}

	@Override
	public BigDecimal brlPerUsd() {
		return select("USD", "BRL", clock.instant()).quote().rate();
	}

	public FxSelection select(String baseCurrency, String quoteCurrency, Instant at) {
		OffsetDateTime limit = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
		List<FxQuote> quotes = rates
				.findByBaseCurrencyAndQuoteCurrencyAndValidFromLessThanEqual(baseCurrency, quoteCurrency, limit)
				.stream()
				.map(DbFxRateProvider::toQuote)
				.toList();
		return validity.select(quotes, at);
	}

	private static FxQuote toQuote(FxRateEntity rate) {
		return new FxQuote(
				rate.getId(),
				rate.getBaseCurrency(),
				rate.getQuoteCurrency(),
				rate.getRate(),
				rate.getValidFrom());
	}

}
