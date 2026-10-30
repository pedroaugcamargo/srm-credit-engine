package com.srmasset.creditengine.application;

import org.springframework.stereotype.Service;

import com.srmasset.creditengine.domain.fx.ExternalFxClient;
import com.srmasset.creditengine.domain.fx.ExternalFxQuote;
import com.srmasset.creditengine.domain.fx.FxQuote;

@Service
public class FxRateRefreshService {

	static final String EXTERNAL_SOURCE = "EXTERNAL";

	private final ExternalFxClient client;
	private final FxRateService rates;

	public FxRateRefreshService(ExternalFxClient client, FxRateService rates) {
		this.client = client;
		this.rates = rates;
	}

	public FxQuote refresh() {
		ExternalFxQuote quote = client.fetch("USD", "BRL");
		return rates.register(
				quote.base(),
				quote.quote(),
				quote.rate().toPlainString(),
				quote.validFrom(),
				EXTERNAL_SOURCE);
	}

}
