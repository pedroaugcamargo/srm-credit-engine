package com.srmasset.creditengine.infra.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.srmasset.creditengine.domain.fx.ExternalFxClientException;
import com.srmasset.creditengine.domain.fx.ExternalFxQuote;
import com.srmasset.creditengine.domain.fx.ExternalFxServerException;
import com.srmasset.creditengine.domain.fx.ExternalFxTimeoutException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

class ResilientExternalFxClientTest {

	private static final OffsetDateTime VALID_FROM = OffsetDateTime.parse("2026-10-29T10:00:00-03:00");

	private static final ExternalFxQuote RECOVERED = new ExternalFxQuote(
			"USD",
			"BRL",
			new BigDecimal("5.6000"),
			VALID_FROM);

	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

	@AfterEach
	void shutdown() {
		scheduler.shutdownNow();
	}

	@Test
	void retriesAfterATimeoutAndReturnsTheNextQuote() {
		AtomicInteger calls = new AtomicInteger();
		ResilientExternalFxClient client = client((base, quote) -> {
			if (calls.incrementAndGet() == 1) {
				sleepUntilInterrupted();
			}
			return RECOVERED;
		}, Duration.ofMillis(50), 2);

		ExternalFxQuote quote = client.fetch("USD", "BRL");

		assertEquals(0, new BigDecimal("5.6000").compareTo(quote.rate()));
		assertEquals("USD", quote.base());
		assertEquals("BRL", quote.quote());
		assertEquals(VALID_FROM, quote.validFrom());
		assertEquals(2, calls.get());
	}

	@Test
	void stopsAfterTwoRetriesWhenEveryAttemptTimesOut() {
		AtomicInteger calls = new AtomicInteger();
		ResilientExternalFxClient client = client((base, quote) -> {
			calls.incrementAndGet();
			sleepUntilInterrupted();
			throw new ExternalFxTimeoutException();
		}, Duration.ofMillis(50), 2);

		assertThrows(ExternalFxTimeoutException.class, () -> client.fetch("USD", "BRL"));
		assertEquals(3, calls.get());
	}

	@Test
	void doesNotRetryAClientError() {
		AtomicInteger calls = new AtomicInteger();
		ResilientExternalFxClient client = client((base, quote) -> {
			calls.incrementAndGet();
			throw new ExternalFxClientException();
		}, Duration.ofMillis(50), 2);

		assertThrows(ExternalFxClientException.class, () -> client.fetch("USD", "BRL"));
		assertEquals(1, calls.get());
	}

	@Test
	void opensTheCircuitWhenHalfOfTenCallsFail() {
		AtomicInteger calls = new AtomicInteger();
		AtomicBoolean fail = new AtomicBoolean();
		ResilientExternalFxClient client = client((base, quote) -> {
			calls.incrementAndGet();
			if (fail.get()) {
				throw new ExternalFxServerException();
			}
			return new ExternalFxQuote("USD", "BRL", new BigDecimal("5.4321"), VALID_FROM);
		}, Duration.ofMillis(50), 2);

		for (int attempt = 1; attempt <= 10; attempt++) {
			fail.set(attempt % 2 == 0);
			if (attempt % 2 == 0) {
				assertThrows(ExternalFxServerException.class, () -> client.fetch("USD", "BRL"));
			} else {
				client.fetch("USD", "BRL");
			}
		}

		assertEquals(20, calls.get());
		assertThrows(CallNotPermittedException.class, () -> client.fetch("USD", "BRL"));
		assertEquals(20, calls.get());
	}

	@Test
	void mockReturnsTheConfiguredRateWithoutCallingTheNetwork() {
		ConfigurableExternalFxClient mock = new ConfigurableExternalFxClient(properties(Duration.ofSeconds(2), 2));

		ExternalFxQuote quote = mock.fetch("USD", "BRL");

		assertEquals(0, new BigDecimal("5.4321").compareTo(quote.rate()));
		assertEquals(VALID_FROM, quote.validFrom());
	}

	private ResilientExternalFxClient client(
			com.srmasset.creditengine.domain.fx.ExternalFxClient delegate,
			Duration timeout,
			int maxRetries) {
		return ResilientExternalFxClient.create(delegate, properties(timeout, maxRetries), scheduler);
	}

	private static ExternalFxProviderProperties properties(Duration timeout, int maxRetries) {
		return new ExternalFxProviderProperties(
				Duration.ZERO,
				ExternalFxProviderProperties.Failure.NONE,
				"5.4321",
				VALID_FROM,
				timeout,
				maxRetries,
				Duration.ofMillis(1),
				10,
				50f,
				Duration.ofSeconds(30),
				false,
				Duration.ofHours(1));
	}

	private static void sleepUntilInterrupted() {
		try {
			Thread.sleep(Duration.ofSeconds(30));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new ExternalFxTimeoutException(exception);
		}
	}

}
