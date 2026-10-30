package com.srmasset.creditengine.domain.fx;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "credit-engine.fx")
public final class FxProperties {

	private final Duration maxAge;

	public FxProperties(@DefaultValue("PT24H") Duration maxAge) {
		this.maxAge = maxAge;
	}

	public Duration maxAge() {
		return maxAge;
	}

}
