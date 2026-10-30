package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import com.srmasset.creditengine.application.StatementQueryTimeoutException;
import com.srmasset.creditengine.domain.fx.ExternalFxClientException;
import com.srmasset.creditengine.domain.fx.ExternalFxTimeoutException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

class FxProviderProblemDetailTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void timeoutBecomes503WithTheProviderMessage() {
		ProblemDetail problem = handler.fxProviderUnavailable(new ExternalFxTimeoutException());

		assertEquals(503, problem.getStatus());
		assertEquals("FX_PROVIDER_UNAVAILABLE", problem.getProperties().get("code"));
		assertEquals("Provedor de câmbio não respondeu a tempo.", problem.getDetail());
	}

	@Test
	void clientRefusalBecomes503WithTheProviderMessage() {
		ProblemDetail problem = handler.fxProviderUnavailable(new ExternalFxClientException());

		assertEquals(503, problem.getStatus());
		assertEquals("FX_PROVIDER_UNAVAILABLE", problem.getProperties().get("code"));
		assertEquals("Provedor de câmbio recusou a consulta.", problem.getDetail());
	}

	@Test
	void openCircuitBecomes503WithoutTheBreakerMessage() {
		CircuitBreaker breaker = CircuitBreaker.ofDefaults("externalFx");
		breaker.transitionToOpenState();

		ProblemDetail problem = handler
				.fxProviderUnavailable(CallNotPermittedException.createCallNotPermittedException(breaker));

		assertEquals(503, problem.getStatus());
		assertEquals("FX_PROVIDER_UNAVAILABLE", problem.getProperties().get("code"));
		assertEquals("Provedor de câmbio indisponível.", problem.getDetail());
	}

	@Test
	void statementTimeoutBecomes503() {
		ProblemDetail problem = handler.statementTimeout(new StatementQueryTimeoutException(new RuntimeException()));

		assertEquals(503, problem.getStatus());
		assertEquals("STATEMENT_QUERY_TIMEOUT", problem.getProperties().get("code"));
		assertEquals("A consulta do extrato excedeu o tempo limite.", problem.getDetail());
	}

}
