package com.srmasset.creditengine.infra.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.srmasset.creditengine.domain.fx.ExternalFxClient;
import com.srmasset.creditengine.domain.fx.ExternalFxServerException;

@SpringBootTest(properties = "credit-engine.fx.provider.failure=SERVER_ERROR")
class ExternalFxProviderFailureTest {

	@Autowired
	private ExternalFxClient client;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void failureLeavesTheStoredRateUntouched() {
		ResilientExternalFxClient resilient = (ResilientExternalFxClient) client;
		assertEquals(Duration.ofSeconds(2), resilient.configuredTimeout());
		assertEquals(3, resilient.configuredMaxAttempts());
		assertEquals(10, resilient.circuitWindowSize());
		assertEquals(50f, resilient.circuitFailureRate());

		int before = fxCount();
		BigDecimal seedBefore = seedRate();

		assertThrows(ExternalFxServerException.class, () -> client.fetch("USD", "BRL"));

		assertEquals(before, fxCount());
		assertEquals(0, new BigDecimal("5.4321").compareTo(seedRate()));
		assertEquals(0, seedBefore.compareTo(seedRate()));
	}

	private int fxCount() {
		return jdbc.queryForObject("select count(*) from fx_rates", Integer.class);
	}

	private BigDecimal seedRate() {
		return jdbc.queryForObject(
				"select rate from fx_rates where source = ? and base_currency = ? and quote_currency = ?",
				BigDecimal.class,
				"SEED",
				"USD",
				"BRL");
	}

}
