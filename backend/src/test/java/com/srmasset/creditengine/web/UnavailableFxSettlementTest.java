package com.srmasset.creditengine.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(UnavailableFxSettlementTest.OperationBeforeAnyRate.class)
class UnavailableFxSettlementTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void rejectsUsdSettlementWhenNoRateIsValidYet() throws Exception {
		String cedenteId = jdbc.queryForObject("select id::text from cedentes where documento = 'DOC-DEMO-001'",
				String.class);
		MvcResult created = mockMvc
				.perform(post("/api/v1/receivables").contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "cedenteId": "%s",
						  "type": "DUPLICATA_MERCANTIL",
						  "faceValue": "100000.00",
						  "dueDate": "2026-04-01"
						}
						""".formatted(cedenteId))).andExpect(status().isCreated()).andReturn();
		String receivableId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(post("/api/v1/settlements").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "receivableId": "%s",
						  "paymentCurrency": "USD"
						}
						""".formatted(receivableId))).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));
	}

	@TestConfiguration
	static class OperationBeforeAnyRate {

		@Bean
		@Primary
		Clock fixedOperationClock() {
			return Clock.fixed(LocalDateTime.parse("2025-12-31T12:00:00").atZone(SAO_PAULO).toInstant(), SAO_PAULO);
		}

	}

}
