package com.srmasset.creditengine.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SettlementStatementTest.OperationAtNoonOnNewYear.class)
class SettlementStatementTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final String DUE_DATE = "2026-04-01";

	private static final String FROM = "2026-01-01T00:00:00-03:00";

	private static final String TO = "2026-01-02T00:00:00-03:00";

	private static final String SETTLED_AT = "2026-01-01T12:00:00-03:00";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void listsSettlementsOfSeveralCedentesAndCurrencies() throws Exception {
		String cedente2 = cedenteId("DOC-DEMO-002");
		String cedente3 = cedenteId("DOC-DEMO-003");
		settle(register(cedente2, "CHEQUE"), "BRL");
		settle(register(cedente3, "DUPLICATA_MERCANTIL"), "BRL");
		settle(register(cedente3, "DUPLICATA_MERCANTIL"), "USD");

		mockMvc.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO)).andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.items[?(@.cedenteNome == 'Cedente Demonstração 2')].paymentCurrency")
						.value(hasItem("BRL")))
				.andExpect(jsonPath("$.items[?(@.cedenteNome == 'Cedente Demonstração 2')].faceValue")
						.value(hasItem("100000.00")))
				.andExpect(jsonPath("$.items[?(@.cedenteNome == 'Cedente Demonstração 2')].presentValueBrl")
						.value(hasItem("90194.27")))
				.andExpect(jsonPath("$.items[?(@.cedenteNome == 'Cedente Demonstração 2')].fxRate")
						.value(hasItem(nullValue())))
				.andExpect(jsonPath("$.items[?(@.cedenteNome == 'Cedente Demonstração 2')].netAmount")
						.value(hasItem("90194.27")))
				.andExpect(jsonPath("$.items[?(@.cedenteNome == 'Cedente Demonstração 2')].settledAt")
						.value(hasItem(SETTLED_AT)))
				.andExpect(jsonPath(
						"$.items[?(@.cedenteNome == 'Cedente Demonstração 3' && @.paymentCurrency == 'USD')].faceValue")
						.value(hasItem("100000.00")))
				.andExpect(jsonPath(
						"$.items[?(@.cedenteNome == 'Cedente Demonstração 3' && @.paymentCurrency == 'USD')].presentValueBrl")
						.value(hasItem("92859.94")))
				.andExpect(jsonPath(
						"$.items[?(@.cedenteNome == 'Cedente Demonstração 3' && @.paymentCurrency == 'USD')].fxRate")
						.value(hasItem("5.4321")))
				.andExpect(jsonPath(
						"$.items[?(@.cedenteNome == 'Cedente Demonstração 3' && @.paymentCurrency == 'USD')].netAmount")
						.value(hasItem("17094.67")))
				.andExpect(jsonPath(
						"$.items[?(@.cedenteNome == 'Cedente Demonstração 3' && @.paymentCurrency == 'BRL')].presentValueBrl")
						.value(hasItem("92859.94")))
				.andExpect(jsonPath(
						"$.items[?(@.cedenteNome == 'Cedente Demonstração 3' && @.paymentCurrency == 'BRL')].fxRate")
						.value(hasItem(nullValue())))
				.andExpect(jsonPath(
						"$.items[?(@.cedenteNome == 'Cedente Demonstração 3' && @.paymentCurrency == 'BRL')].netAmount")
						.value(hasItem("92859.94")));

		mockMvc.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO).param("cedenteId", cedente2)
				.param("currency", "BRL")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].cedenteId").value(cedente2))
				.andExpect(jsonPath("$.items[0].cedenteNome").value("Cedente Demonstração 2"))
				.andExpect(jsonPath("$.items[0].presentValueBrl").value("90194.27"))
				.andExpect(jsonPath("$.items[0].netAmount").value("90194.27"))
				.andExpect(jsonPath("$.items[0].fxRate").value(nullValue()));

		mockMvc.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO).param("cedenteId", cedente3)
				.param("currency", "USD")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.items[0].netAmount").value("17094.67"))
				.andExpect(jsonPath("$.items[0].fxRate").value("5.4321"));

		MvcResult firstPage = mockMvc
				.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO).param("cedenteId", cedente3)
						.param("page", "0").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.items.length()").value(1)).andReturn();
		MvcResult secondPage = mockMvc
				.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO).param("cedenteId", cedente3)
						.param("page", "1").param("size", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andReturn();

		String firstCurrency = com.jayway.jsonpath.JsonPath.read(firstPage.getResponse().getContentAsString(),
				"$.items[0].paymentCurrency");
		String secondCurrency = com.jayway.jsonpath.JsonPath.read(secondPage.getResponse().getContentAsString(),
				"$.items[0].paymentCurrency");
		String firstNet = com.jayway.jsonpath.JsonPath.read(firstPage.getResponse().getContentAsString(),
				"$.items[0].netAmount");
		String secondNet = com.jayway.jsonpath.JsonPath.read(secondPage.getResponse().getContentAsString(),
				"$.items[0].netAmount");
		assertNotEquals(firstCurrency, secondCurrency);
		assertEquals(netOf(firstCurrency), firstNet);
		assertEquals(netOf(secondCurrency), secondNet);
	}

	@Test
	void rejectsAMissingInvertedOrTooLongPeriod() throws Exception {
		mockMvc.perform(get("/api/v1/settlements").param("to", TO)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("STATEMENT_QUERY_INVALID"));

		mockMvc.perform(get("/api/v1/settlements").param("from", TO).param("to", FROM))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("O período está invertido."))
				.andExpect(jsonPath("$.code").value("STATEMENT_QUERY_INVALID"));

		mockMvc.perform(get("/api/v1/settlements").param("from", "2025-01-01T00:00:00-03:00").param("to",
				"2026-01-03T00:00:00-03:00")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("O período não pode ser maior que 366 dias."))
				.andExpect(jsonPath("$.code").value("STATEMENT_QUERY_INVALID"));

		mockMvc.perform(get("/api/v1/settlements").param("from", "2025-01-01T00:00:00-03:00").param("to",
				"2026-01-02T00:00:00-03:00")).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO).param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("size não pode ser maior que 100."))
				.andExpect(jsonPath("$.code").value("STATEMENT_QUERY_INVALID"));

		mockMvc.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO).param("page", "50001"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("page é grande demais."))
				.andExpect(jsonPath("$.code").value("STATEMENT_QUERY_INVALID"));

		mockMvc.perform(get("/api/v1/settlements").param("from", FROM).param("to", TO).param("page", "2147483647"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("page é grande demais."))
				.andExpect(jsonPath("$.code").value("STATEMENT_QUERY_INVALID"));
	}

	private static String netOf(String currency) {
		return "USD".equals(currency) ? "17094.67" : "92859.94";
	}

	private String cedenteId(String documento) {
		return jdbc.queryForObject("select id::text from cedentes where documento = ?", String.class, documento);
	}

	private String register(String cedenteId, String type) throws Exception {
		MvcResult created = mockMvc
				.perform(post("/api/v1/receivables").contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "cedenteId": "%s",
						  "type": "%s",
						  "faceValue": "100000.00",
						  "dueDate": "%s"
						}
						""".formatted(cedenteId, type, DUE_DATE))).andExpect(status().isCreated()).andReturn();
		return com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
	}

	private void settle(String receivableId, String currency) throws Exception {
		mockMvc.perform(post("/api/v1/settlements").header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content("""
						{
						  "receivableId": "%s",
						  "paymentCurrency": "%s"
						}
						""".formatted(receivableId, currency))).andExpect(status().isCreated());
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
