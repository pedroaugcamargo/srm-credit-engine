package com.srmasset.creditengine.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.srmasset.creditengine.domain.fx.FxQuote;
import com.srmasset.creditengine.domain.fx.FxSelection;
import com.srmasset.creditengine.persistence.DbFxRateProvider;
import com.srmasset.creditengine.persistence.FxRateEntity;
import com.srmasset.creditengine.persistence.FxRateRepository;

@Service
public class FxRateService {

	static final String MANUAL_SOURCE = "MANUAL";

	private final FxRateRepository rates;
	private final DbFxRateProvider provider;
	private final Clock clock;

	public FxRateService(FxRateRepository rates, DbFxRateProvider provider, Clock clock) {
		this.rates = rates;
		this.provider = provider;
		this.clock = clock;
	}

	@Transactional
	public FxQuote register(String base, String quote, String rate, OffsetDateTime validFrom) {
		FxRateEntity entity = new FxRateEntity();
		entity.setBaseCurrency(base);
		entity.setQuoteCurrency(quote);
		entity.setRate(new BigDecimal(rate));
		entity.setValidFrom(validFrom);
		entity.setSource(MANUAL_SOURCE);
		entity.setCreatedAt(OffsetDateTime.now(clock));
		FxRateEntity saved = rates.saveAndFlush(entity);
		return new FxQuote(saved.getId(), saved.getBaseCurrency(), saved.getQuoteCurrency(), saved.getRate(), saved.getValidFrom());
	}

	@Transactional(readOnly = true)
	public FxSelection current(String base, String quote) {
		return provider.select(base, quote, clock.instant());
	}

}
