package com.srmasset.creditengine.infra.fx;

import java.math.BigDecimal;
import java.time.Duration;

import com.srmasset.creditengine.domain.fx.ExternalFxClient;
import com.srmasset.creditengine.domain.fx.ExternalFxClientException;
import com.srmasset.creditengine.domain.fx.ExternalFxQuote;
import com.srmasset.creditengine.domain.fx.ExternalFxServerException;
import com.srmasset.creditengine.domain.fx.ExternalFxTimeoutException;

public final class ConfigurableExternalFxClient implements ExternalFxClient {

	private final ExternalFxProviderProperties properties;

	public ConfigurableExternalFxClient(ExternalFxProviderProperties properties) {
		this.properties = properties;
	}

	@Override
	public ExternalFxQuote fetch(String base, String quote) {
		if (properties.failure() == ExternalFxProviderProperties.Failure.TIMEOUT) {
			sleep(Duration.ofHours(1));
		} else if (!properties.delay().isZero()) {
			sleep(properties.delay());
		}
		return switch (properties.failure()) {
			case SERVER_ERROR -> throw new ExternalFxServerException();
			case CLIENT_ERROR -> throw new ExternalFxClientException();
			case NONE, TIMEOUT -> new ExternalFxQuote(
					base,
					quote,
					new BigDecimal(properties.rate()),
					properties.validFrom());
		};
	}

	private static void sleep(Duration delay) {
		try {
			Thread.sleep(delay);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ExternalFxTimeoutException(exception);
		}
	}

}
