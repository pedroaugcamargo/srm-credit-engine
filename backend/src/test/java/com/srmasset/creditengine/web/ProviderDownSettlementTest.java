package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.srmasset.creditengine.domain.fx.ExternalFxServerException;

import jakarta.servlet.ServletException;

@SpringBootTest(properties = "credit-engine.fx.provider.failure=SERVER_ERROR")
@AutoConfigureMockMvc
@Import(ProviderDownSettlementTest.OperationAfterTheSeedExpired.class)
class ProviderDownSettlementTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void staleUsdSettlementIsRefusedWhenTheProviderIsDown() throws Exception {
		assertEquals(0, new BigDecimal("5.4321").compareTo(seedRate()));
		int fxRows = fxCount();

		ServletException refreshFailure = assertThrows(ServletException.class,
				() -> mockMvc.perform(post("/api/v1/fx-rates/refresh")).andReturn());
		assertInstanceOf(ExternalFxServerException.class, refreshFailure.getCause());

		assertEquals(fxRows, fxCount());
		assertEquals(0, new BigDecimal("5.4321").compareTo(seedRate()));

		String receivableId = registerReceivable("2026-04-03");
		mockMvc.perform(post("/api/v1/settlements").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "receivableId": "%s",
						  "paymentCurrency": "USD"
						}
						""".formatted(receivableId))).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("FX_RATE_STALE"));

		mockMvc.perform(get("/api/v1/receivables/" + receivableId)).andExpect(jsonPath("$.status").value("OPEN"));
		assertEquals(0, new BigDecimal("5.4321").compareTo(seedRate()));
	}

	@Test
	void brlSettlementStillPricesWhenTheProviderIsDown() throws Exception {
		String receivableId = registerReceivable("2026-04-03");

		mockMvc.perform(post("/api/v1/settlements").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "receivableId": "%s",
						  "paymentCurrency": "BRL"
						}
						""".formatted(receivableId))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentCurrency").value("BRL"))
				.andExpect(jsonPath("$.faceValue").value("100000.00")).andExpect(jsonPath("$.termMonths").value("3"))
				.andExpect(jsonPath("$.baseRate").value("0.01")).andExpect(jsonPath("$.spread").value("0.015"))
				.andExpect(jsonPath("$.presentValueBrl").value("92859.94"))
				.andExpect(jsonPath("$.discountBrl").value("7140.06"))
				.andExpect(jsonPath("$.fxRate").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.netAmount").value("92859.94"));
	}

	private String registerReceivable(String dueDate) throws Exception {
		String cedenteId = jdbc.queryForObject("select id::text from cedentes where documento = 'DOC-DEMO-001'",
				String.class);
		MvcResult created = mockMvc
				.perform(post("/api/v1/receivables").contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "cedenteId": "%s",
						  "type": "DUPLICATA_MERCANTIL",
						  "faceValue": "100000.00",
						  "dueDate": "%s"
						}
						""".formatted(cedenteId, dueDate))).andExpect(status().isCreated()).andReturn();
		return com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
	}

	private BigDecimal seedRate() {
		return jdbc.queryForObject("select rate from fx_rates where source = 'SEED'", BigDecimal.class);
	}

	private int fxCount() {
		return jdbc.queryForObject("select count(*) from fx_rates", Integer.class);
	}

	@TestConfiguration
	static class OperationAfterTheSeedExpired {

		@Bean
		@Primary
		Clock fixedOperationClock() {
			return Clock.fixed(LocalDateTime.parse("2026-01-03T12:00:00").atZone(SAO_PAULO).toInstant(), SAO_PAULO);
		}

	}

}
