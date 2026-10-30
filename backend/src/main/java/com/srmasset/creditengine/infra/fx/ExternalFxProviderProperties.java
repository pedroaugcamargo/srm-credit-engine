package com.srmasset.creditengine.infra.fx;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "credit-engine.fx.provider")
public final class ExternalFxProviderProperties {

	public enum Failure {
		NONE,
		TIMEOUT,
		SERVER_ERROR,
		CLIENT_ERROR
	}

	private final Duration delay;
	private final Failure failure;
	private final String rate;
	private final OffsetDateTime validFrom;
	private final Duration timeout;
	private final int maxRetries;
	private final Duration retryInitialInterval;
	private final int circuitWindowSize;
	private final float circuitFailureRate;
	private final Duration circuitOpenWait;
	private final boolean refreshEnabled;
	private final Duration refreshInterval;

	public ExternalFxProviderProperties(
			@DefaultValue("PT0S") Duration delay,
			@DefaultValue("NONE") Failure failure,
			@DefaultValue("5.4321") String rate,
			@DefaultValue("2026-10-29T10:00:00-03:00") OffsetDateTime validFrom,
			@DefaultValue("PT2S") Duration timeout,
			@DefaultValue("2") int maxRetries,
			@DefaultValue("PT0.2S") Duration retryInitialInterval,
			@DefaultValue("10") int circuitWindowSize,
			@DefaultValue("50") float circuitFailureRate,
			@DefaultValue("PT30S") Duration circuitOpenWait,
			@DefaultValue("false") boolean refreshEnabled,
			@DefaultValue("PT1H") Duration refreshInterval) {
		this.delay = delay;
		this.failure = failure;
		this.rate = rate;
		this.validFrom = validFrom;
		this.timeout = timeout;
		this.maxRetries = maxRetries;
		this.retryInitialInterval = retryInitialInterval;
		this.circuitWindowSize = circuitWindowSize;
		this.circuitFailureRate = circuitFailureRate;
		this.circuitOpenWait = circuitOpenWait;
		this.refreshEnabled = refreshEnabled;
		this.refreshInterval = refreshInterval;
	}

	public Duration delay() {
		return delay;
	}

	public Failure failure() {
		return failure;
	}

	public String rate() {
		return rate;
	}

	public OffsetDateTime validFrom() {
		return validFrom;
	}

	public Duration timeout() {
		return timeout;
	}

	public int maxRetries() {
		return maxRetries;
	}

	public Duration retryInitialInterval() {
		return retryInitialInterval;
	}

	public int circuitWindowSize() {
		return circuitWindowSize;
	}

	public float circuitFailureRate() {
		return circuitFailureRate;
	}

	public Duration circuitOpenWait() {
		return circuitOpenWait;
	}

	public boolean refreshEnabled() {
		return refreshEnabled;
	}

	public Duration refreshInterval() {
		return refreshInterval;
	}

}
