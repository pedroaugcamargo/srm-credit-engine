package com.srmasset.creditengine.infra.fx;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;

import com.srmasset.creditengine.domain.fx.ExternalFxClient;
import com.srmasset.creditengine.domain.fx.ExternalFxQuote;
import com.srmasset.creditengine.domain.fx.ExternalFxServerException;
import com.srmasset.creditengine.domain.fx.ExternalFxTimeoutException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

public final class ResilientExternalFxClient implements ExternalFxClient {

	private final ExternalFxClient delegate;
	private final TimeLimiter timeLimiter;
	private final Retry retry;
	private final CircuitBreaker circuitBreaker;
	private final ScheduledExecutorService scheduler;

	public ResilientExternalFxClient(
			ExternalFxClient delegate,
			TimeLimiter timeLimiter,
			Retry retry,
			CircuitBreaker circuitBreaker,
			ScheduledExecutorService scheduler) {
		this.delegate = delegate;
		this.timeLimiter = timeLimiter;
		this.retry = retry;
		this.circuitBreaker = circuitBreaker;
		this.scheduler = scheduler;
	}

	public static ResilientExternalFxClient create(
			ExternalFxClient delegate,
			ExternalFxProviderProperties properties,
			ScheduledExecutorService scheduler) {
		TimeLimiter timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom()
				.timeoutDuration(properties.timeout())
				.cancelRunningFuture(true)
				.build());
		Retry retry = Retry.of("externalFx", RetryConfig.custom()
				.maxAttempts(properties.maxRetries() + 1)
				.intervalFunction(IntervalFunction.ofExponentialBackoff(properties.retryInitialInterval(), 2d))
				.retryOnException(ResilientExternalFxClient::isRetryable)
				.build());
		CircuitBreaker circuitBreaker = CircuitBreaker.of("externalFx", CircuitBreakerConfig.custom()
				.slidingWindowType(SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(properties.circuitWindowSize())
				.minimumNumberOfCalls(properties.circuitWindowSize())
				.failureRateThreshold(properties.circuitFailureRate())
				.waitDurationInOpenState(properties.circuitOpenWait())
				.permittedNumberOfCallsInHalfOpenState(1)
				.build());
		return new ResilientExternalFxClient(delegate, timeLimiter, retry, circuitBreaker, scheduler);
	}

	@Override
	public ExternalFxQuote fetch(String base, String quote) {
		try {
			return circuitBreaker.executeCheckedSupplier(() -> retry.executeCheckedSupplier(() -> timeLimiter
					.executeFutureSupplier(() -> CompletableFuture.supplyAsync(() -> delegate.fetch(base, quote), scheduler))));
		} catch (TimeoutException exception) {
			throw new ExternalFxTimeoutException(exception);
		} catch (RuntimeException | Error exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IllegalStateException(exception);
		}
	}

	Duration configuredTimeout() {
		return timeLimiter.getTimeLimiterConfig().getTimeoutDuration();
	}

	int configuredMaxAttempts() {
		return retry.getRetryConfig().getMaxAttempts();
	}

	int circuitWindowSize() {
		return circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize();
	}

	float circuitFailureRate() {
		return circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold();
	}

	private static boolean isRetryable(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof TimeoutException || current instanceof ExternalFxTimeoutException
					|| current instanceof ExternalFxServerException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

}
