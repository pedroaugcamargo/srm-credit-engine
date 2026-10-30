package com.srmasset.creditengine.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.srmasset.creditengine.application.BaseRateUnavailableException;

@Component
public final class DbBaseRateProvider {

	private final BaseRateRepository rates;

	public DbBaseRateProvider(BaseRateRepository rates) {
		this.rates = rates;
	}

	public BigDecimal rateAt(Instant at) {
		OffsetDateTime limit = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
		return rates.findByValidFromLessThanEqual(limit).stream()
				.max(Comparator.comparing(rate -> rate.getValidFrom().toInstant())).map(BaseRateEntity::getRate)
				.orElseThrow(BaseRateUnavailableException::new);
	}

}
