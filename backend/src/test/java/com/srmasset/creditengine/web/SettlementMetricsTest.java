package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Transactional
@Import(SettlementMetricsTest.OperationAtNoonOnNewYear.class)
class SettlementMetricsTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final String DUE_DATE = "2026-04-01";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MeterRegistry meters;

	@Test
	void incrementsTheSettlementCounterForEachResult() throws Exception {
		String receivableId = registerReceivable();
		UUID key = UUID.randomUUID();

		double created = settlements("BRL", "created");
		double priced = pricingCount();
		settle(receivableId, "BRL", key).andExpect(status().isCreated());
		assertEquals(created + 1, settlements("BRL", "created"));
		assertEquals(priced + 1, pricingCount());

		double replayed = settlements("BRL", "replayed");
		settle(receivableId, "BRL", key).andExpect(status().isOk());
		assertEquals(replayed + 1, settlements("BRL", "replayed"));

		double rejected = settlements("USD", "rejected");
		settle(receivableId, "USD", key).andExpect(status().isUnprocessableContent());
		assertEquals(rejected + 1, settlements("USD", "rejected"));

		double conflict = settlements("BRL", "conflict");
		settle(receivableId, "BRL", UUID.randomUUID()).andExpect(status().isConflict());
		assertEquals(conflict + 1, settlements("BRL", "conflict"));

		double providerCalls = providerCalls("success");
		mockMvc.perform(post("/api/v1/fx-rates/refresh")).andExpect(status().isCreated());
		assertEquals(providerCalls + 1, providerCalls("success"));

		String scrape = prometheus();
		assertEquals(settlements("BRL", "created"),
				sample(scrape, "settlements_total", "currency=\"BRL\"", "result=\"created\""));
		assertEquals(settlements("BRL", "replayed"),
				sample(scrape, "settlements_total", "currency=\"BRL\"", "result=\"replayed\""));
		assertEquals(settlements("USD", "rejected"),
				sample(scrape, "settlements_total", "currency=\"USD\"", "result=\"rejected\""));
		assertEquals(settlements("BRL", "conflict"),
				sample(scrape, "settlements_total", "currency=\"BRL\"", "result=\"conflict\""));
		assertEquals(providerCalls("success"), sample(scrape, "fx_provider_calls_total", "outcome=\"success\""));
		assertEquals(1, sample(scrape, "resilience4j_circuitbreaker_state", "name=\"externalFx\"", "state=\"closed\""));
		assertTrue(scrape.contains("pricing_engine_duration_seconds"));
		assertTrue(scrape.contains("quantile=\"0.95\""));
		assertFalse(scrape.contains("settlements_total_total"));
		assertFalse(scrape.contains("fx_provider_calls_total_total"));
	}

	private double settlements(String currency, String result) {
		Counter counter = meters.find("settlements").tag("currency", currency).tag("result", result).counter();
		return counter == null ? 0 : counter.count();
	}

	private double providerCalls(String outcome) {
		Counter counter = meters.find("fx_provider_calls").tag("outcome", outcome).counter();
		return counter == null ? 0 : counter.count();
	}

	private double pricingCount() {
		Timer timer = meters.find("pricing_engine_duration").timer();
		return timer == null ? 0 : timer.count();
	}

	private String prometheus() throws Exception {
		return mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private String registerReceivable() throws Exception {
		String cedenteId = jdbc.queryForObject("select id::text from cedentes where documento = ?", String.class,
				"DOC-DEMO-001");
		MvcResult created = mockMvc
				.perform(post("/api/v1/receivables").contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "cedenteId": "%s",
						  "type": "DUPLICATA_MERCANTIL",
						  "faceValue": "100000.00",
						  "dueDate": "%s"
						}
						""".formatted(cedenteId, DUE_DATE))).andExpect(status().isCreated()).andReturn();
		return com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
	}

	private org.springframework.test.web.servlet.ResultActions settle(String receivableId, String currency, UUID key)
			throws Exception {
		return mockMvc.perform(post("/api/v1/settlements").header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "receivableId": "%s",
						  "paymentCurrency": "%s"
						}
						""".formatted(receivableId, currency)));
	}

	private static double sample(String scrape, String metric, String... labels) {
		for (String line : scrape.split("\\R")) {
			if (line.startsWith("#") || !(line.startsWith(metric + "{") || line.startsWith(metric + " "))) {
				continue;
			}
			boolean matches = true;
			for (String label : labels) {
				if (!line.contains(label)) {
					matches = false;
					break;
				}
			}
			if (matches) {
				return Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
			}
		}
		throw new AssertionError(scrape);
	}

	@TestConfiguration
	static class OperationAtNoonOnNewYear {

		@Bean
		@Primary
		Clock fixedOperationClock() {
			return Clock.fixed(LocalDateTime.parse("2026-01-01T12:00:00").atZone(SAO_PAULO).toInstant(), SAO_PAULO);
		}

	}

}
