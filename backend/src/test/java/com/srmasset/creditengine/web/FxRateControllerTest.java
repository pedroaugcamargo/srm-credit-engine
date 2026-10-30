package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(FxRateControllerTest.FixedOperationInstant.class)
class FxRateControllerTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final String CURRENT = "/api/v1/fx-rates/current?base=USD&quote=BRL";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void returnsTheSeedRateAsStaleAndThenTheRateValidAtTheClock() throws Exception {
		mockMvc.perform(get(CURRENT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.base").value("USD"))
				.andExpect(jsonPath("$.quote").value("BRL"))
				.andExpect(jsonPath("$.rate").value("5.4321"))
				.andExpect(jsonPath("$.validFrom").value("2026-01-01T00:00:00-03:00"))
				.andExpect(jsonPath("$.stale").value(true));

		mockMvc.perform(post("/api/v1/fx-rates")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "base": "USD",
						  "quote": "BRL",
						  "rate": "5.4321",
						  "validFrom": "2026-10-29T10:00:00-03:00"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rate").value("5.4321"))
				.andExpect(jsonPath("$.validFrom").value("2026-10-29T10:00:00-03:00"));

		mockMvc.perform(get(CURRENT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rate").value("5.4321"))
				.andExpect(jsonPath("$.validFrom").value("2026-10-29T10:00:00-03:00"))
				.andExpect(jsonPath("$.stale").value(false));

		mockMvc.perform(post("/api/v1/fx-rates")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "base": "USD",
						  "quote": "BRL",
						  "rate": "5.6000",
						  "validFrom": "2026-10-29T13:00:00-03:00"
						}
						"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get(CURRENT))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rate").value("5.4321"))
				.andExpect(jsonPath("$.validFrom").value("2026-10-29T10:00:00-03:00"))
				.andExpect(jsonPath("$.stale").value(false));

		assertEquals(3, countRates("USD", "BRL"));
		assertEquals(0, new BigDecimal("5.4321").compareTo(seedRate()));
		assertEquals("MANUAL", sourceAt("2026-10-29T10:00:00-03:00"));
	}

	@Test
	void rejectsARateThatIsNotPositive() throws Exception {
		mockMvc.perform(post("/api/v1/fx-rates")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "base": "USD",
						  "quote": "BRL",
						  "rate": "0.00",
						  "validFrom": "2026-10-29T10:00:00-03:00"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsCurrentRateWhenNoneIsRegistered() throws Exception {
		mockMvc.perform(get("/api/v1/fx-rates/current?base=EUR&quote=BRL"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));
	}

	private int countRates(String base, String quote) {
		Integer count = jdbc.queryForObject(
				"select count(*) from fx_rates where base_currency = ? and quote_currency = ?",
				Integer.class,
				base,
				quote);
		return count;
	}

	private BigDecimal seedRate() {
		return jdbc.queryForObject(
				"select rate from fx_rates where valid_from = timestamptz '2026-01-01 00:00:00-03'",
				BigDecimal.class);
	}

	private String sourceAt(String validFrom) {
		return jdbc.queryForObject(
				"select source from fx_rates where valid_from = ?::timestamptz",
				String.class,
				validFrom);
	}

	@TestConfiguration
	static class FixedOperationInstant {

		@Bean
		@Primary
		Clock fixedOperationClock() {
			return Clock.fixed(
					LocalDateTime.parse("2026-10-29T12:00:00").atZone(SAO_PAULO).toInstant(),
					SAO_PAULO);
		}

	}

}
