package com.srmasset.creditengine.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.srmasset.creditengine.application.BaseRateUnavailableException;
import com.srmasset.creditengine.domain.pricing.BaseRateProvider;

@Component
public final class DbBaseRateProvider implements BaseRateProvider {

	private final BaseRateRepository rates;

	public DbBaseRateProvider(BaseRateRepository rates) {
		this.rates = rates;
	}

	@Override
	public BigDecimal rateAt(Instant at) {
		OffsetDateTime limit = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
		return rates.findByValidFromLessThanEqual(limit).stream()
				.max(Comparator.comparing(rate -> rate.getValidFrom().toInstant())).map(BaseRateEntity::getRate)
				.orElseThrow(BaseRateUnavailableException::new);
	}

}
