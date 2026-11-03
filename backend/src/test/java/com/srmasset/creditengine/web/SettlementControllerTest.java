package com.srmasset.creditengine.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SettlementControllerTest.OperationAtNoonOnNewYear.class)
class SettlementControllerTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final String DUE_DATE = "2026-04-01";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void settlesDuplicataInUsdWithTheGoldenSnapshot() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);
		String fxRateId = jdbc.queryForObject(
				"select id::text from fx_rates where base_currency = 'USD' and quote_currency = 'BRL'", String.class);

		MvcResult created = settle(receivableId, "USD", UUID.randomUUID()).andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.LOCATION))
				.andExpect(jsonPath("$.receivableId").value(receivableId))
				.andExpect(jsonPath("$.paymentCurrency").value("USD"))
				.andExpect(jsonPath("$.faceValue").value("100000.00")).andExpect(jsonPath("$.termMonths").value("3"))
				.andExpect(jsonPath("$.baseRate").value("0.01")).andExpect(jsonPath("$.spread").value("0.015"))
				.andExpect(jsonPath("$.presentValueBrl").value("92859.94"))
				.andExpect(jsonPath("$.discountBrl").value("7140.06")).andExpect(jsonPath("$.fxRateId").value(fxRateId))
				.andExpect(jsonPath("$.fxRate").value("5.4321")).andExpect(jsonPath("$.netAmount").value("17094.67"))
				.andExpect(jsonPath("$.settledAt").value("2026-01-01T12:00:00-03:00")).andReturn();

		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
		org.junit.jupiter.api.Assertions.assertTrue(location.endsWith("/api/v1/settlements/" + id));

		mockMvc.perform(get("/api/v1/receivables/" + receivableId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SETTLED")).andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void returnsTheSettlementAtItsLocation() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);
		MvcResult created = settle(receivableId, "USD", UUID.randomUUID()).andExpect(status().isCreated()).andReturn();
		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		String location = created.getResponse().getHeader(HttpHeaders.LOCATION);

		mockMvc.perform(get(java.net.URI.create(location))).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.receivableId").value(receivableId))
				.andExpect(jsonPath("$.presentValueBrl").value("92859.94"))
				.andExpect(jsonPath("$.fxRate").value("5.4321")).andExpect(jsonPath("$.netAmount").value("17094.67"))
				.andExpect(jsonPath("$.settledAt").value("2026-01-01T12:00:00-03:00"));
	}

	@Test
	void rejectsAnUnknownSettlement() throws Exception {
		mockMvc.perform(get("/api/v1/settlements/" + UUID.randomUUID())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
	}

	@Test
	void settlesDuplicataInBrlWithoutFx() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);

		settle(receivableId, "BRL", UUID.randomUUID()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.paymentCurrency").value("BRL"))
				.andExpect(jsonPath("$.faceValue").value("100000.00")).andExpect(jsonPath("$.termMonths").value("3"))
				.andExpect(jsonPath("$.baseRate").value("0.01")).andExpect(jsonPath("$.spread").value("0.015"))
				.andExpect(jsonPath("$.presentValueBrl").value("92859.94"))
				.andExpect(jsonPath("$.discountBrl").value("7140.06"))
				.andExpect(jsonPath("$.fxRateId").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.fxRate").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.netAmount").value("92859.94"));
	}

	@Test
	void replaysTheOriginalSettlementWhenTheKeyAndBodyMatch() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);
		UUID key = UUID.randomUUID();
		MvcResult created = settle(receivableId, "USD", key).andExpect(status().isCreated())
				.andExpect(jsonPath("$.netAmount").value("17094.67")).andReturn();
		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

		settle(receivableId, "USD", key).andExpect(status().isOk())
				.andExpect(header().string("Idempotent-Replayed", "true")).andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.receivableId").value(receivableId))
				.andExpect(jsonPath("$.paymentCurrency").value("USD"))
				.andExpect(jsonPath("$.faceValue").value("100000.00")).andExpect(jsonPath("$.termMonths").value("3"))
				.andExpect(jsonPath("$.baseRate").value("0.01")).andExpect(jsonPath("$.spread").value("0.015"))
				.andExpect(jsonPath("$.presentValueBrl").value("92859.94"))
				.andExpect(jsonPath("$.discountBrl").value("7140.06")).andExpect(jsonPath("$.fxRate").value("5.4321"))
				.andExpect(jsonPath("$.netAmount").value("17094.67"))
				.andExpect(jsonPath("$.settledAt").value("2026-01-01T12:00:00-03:00"));

		org.junit.jupiter.api.Assertions.assertEquals(1, settlementsOf(receivableId));
	}

	@Test
	void rejectsTheSameKeyWhenTheBodyChanges() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);
		UUID key = UUID.randomUUID();
		settle(receivableId, "BRL", key).andExpect(status().isCreated())
				.andExpect(jsonPath("$.netAmount").value("92859.94"));

		settle(receivableId, "USD", key).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

		org.junit.jupiter.api.Assertions.assertEquals(1, settlementsOf(receivableId));
		mockMvc.perform(get("/api/v1/receivables/" + receivableId)).andExpect(jsonPath("$.status").value("SETTLED"));
	}

	@Test
	void rejectsASecondSettlementOfTheSameReceivable() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);
		settle(receivableId, "BRL", UUID.randomUUID()).andExpect(status().isCreated());

		settle(receivableId, "USD", UUID.randomUUID()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RECEIVABLE_ALREADY_SETTLED"));

		org.junit.jupiter.api.Assertions.assertEquals(1, settlementsOf(receivableId));
	}

	@Test
	void rejectsADueDateThatIsNotAfterTheOperationDate() throws Exception {
		String receivableId = registerReceivable("2026-01-01");

		settle(receivableId, "BRL", UUID.randomUUID()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TERM"));

		mockMvc.perform(get("/api/v1/receivables/" + receivableId)).andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void rejectsAnUnknownReceivable() throws Exception {
		settle(UUID.randomUUID().toString(), "BRL", UUID.randomUUID()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RECEIVABLE_NOT_FOUND"));
	}

	@Test
	void rejectsAMissingIdempotencyKey() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);

		mockMvc.perform(
				post("/api/v1/settlements").contentType(MediaType.APPLICATION_JSON).content(body(receivableId, "BRL")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void keepsTheReceivableOpenWhenTheSettlementInsertFails() throws Exception {
		String receivableId = registerReceivable(DUE_DATE);
		String cedenteId = jdbc.queryForObject("select cedente_id::text from receivables where id = ?::uuid",
				String.class, receivableId);
		jdbc.update(
				"""
						insert into settlements (
						    receivable_id, cedente_id, idempotency_key, request_hash, payment_currency,
						    face_value, term_months, base_rate, spread, present_value_brl, discount_brl, net_amount, settled_at)
						values (?::uuid, ?::uuid, ?::uuid, ?, 'BRL', 100000.00, 3, 0.01, 0.015, 92859.94, 7140.06, 92859.94, timestamptz '2026-01-01 12:00:00-03')
						""",
				receivableId, cedenteId, UUID.randomUUID(), "a".repeat(64));

		settle(receivableId, "BRL", UUID.randomUUID()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RECEIVABLE_ALREADY_SETTLED"));

		mockMvc.perform(get("/api/v1/receivables/" + receivableId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));
		org.junit.jupiter.api.Assertions.assertEquals(1, settlementsOf(receivableId));
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

	private org.springframework.test.web.servlet.ResultActions settle(String receivableId, String currency, UUID key)
			throws Exception {
		return mockMvc.perform(post("/api/v1/settlements").header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body(receivableId, currency)));
	}

	private static String body(String receivableId, String currency) {
		return """
				{
				  "receivableId": "%s",
				  "paymentCurrency": "%s"
				}
				""".formatted(receivableId, currency);
	}

	private int settlementsOf(String receivableId) {
		Integer count = jdbc.queryForObject("select count(*) from settlements where receivable_id = ?::uuid",
				Integer.class, receivableId);
		return count;
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
