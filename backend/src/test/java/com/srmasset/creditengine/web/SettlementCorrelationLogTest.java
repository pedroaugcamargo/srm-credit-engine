package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SettlementCorrelationLogTest.OperationAtNoonOnNewYear.class)
@ExtendWith(OutputCaptureExtension.class)
class SettlementCorrelationLogTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final String DUE_DATE = "2026-04-01";

	private static final String CORRELATION_ID = "corr-demo-001";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void propagatesTheCorrelationHeaderIntoTheSettlementLog(CapturedOutput output) throws Exception {
		Cedente cedente = cedente();
		String receivableId = registerReceivable(cedente.id());

		MvcResult created = settle(receivableId, CORRELATION_ID)
				.andExpect(status().isCreated())
				.andExpect(header().string("X-Correlation-Id", CORRELATION_ID))
				.andReturn();
		String settlementId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

		String event = createdEvent(output.getAll(), CORRELATION_ID);
		assertEquals(CORRELATION_ID, JsonPath.read(event, "$.correlationId"));
		assertEquals("created", JsonPath.read(event, "$.result"));
		assertEquals(receivableId, JsonPath.read(event, "$.receivableId"));
		assertEquals(cedente.id(), JsonPath.read(event, "$.cedenteId"));
		assertEquals(settlementId, JsonPath.read(event, "$.settlementId"));
		assertEquals("Liquidação", JsonPath.read(event, "$.message"));
		assertEquals("INFO", JsonPath.read(event, "$.level"));
		assertEquals("1", JsonPath.read(event, "$['@version']"));
		assertFalse(event.contains(cedente.nome()));
		assertFalse(event.contains(cedente.documento()));
	}

	@Test
	void generatesACorrelationIdWhenTheHeaderIsAbsent(CapturedOutput output) throws Exception {
		Cedente cedente = cedente();
		String receivableId = registerReceivable(cedente.id());

		MvcResult created = settle(receivableId, null)
				.andExpect(status().isCreated())
				.andReturn();
		String correlationId = created.getResponse().getHeader("X-Correlation-Id");
		assertNotNull(correlationId);
		UUID.fromString(correlationId);

		String settlementId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		String event = createdEvent(output.getAll(), correlationId);
		assertEquals(correlationId, JsonPath.read(event, "$.correlationId"));
		assertEquals("created", JsonPath.read(event, "$.result"));
		assertEquals(receivableId, JsonPath.read(event, "$.receivableId"));
		assertEquals(cedente.id(), JsonPath.read(event, "$.cedenteId"));
		assertEquals(settlementId, JsonPath.read(event, "$.settlementId"));
		assertFalse(event.contains(cedente.nome()));
		assertFalse(event.contains(cedente.documento()));
	}

	private Cedente cedente() {
		return jdbc.queryForObject(
				"select id::text, nome, documento from cedentes where documento = ?",
				(rs, rowNum) -> new Cedente(rs.getString("id"), rs.getString("nome"), rs.getString("documento")),
				"DOC-DEMO-001");
	}

	private String registerReceivable(String cedenteId) throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/receivables")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "cedenteId": "%s",
						  "type": "DUPLICATA_MERCANTIL",
						  "faceValue": "100000.00",
						  "dueDate": "%s"
						}
						""".formatted(cedenteId, DUE_DATE)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
	}

	private org.springframework.test.web.servlet.ResultActions settle(String receivableId, String correlationId) throws Exception {
		var request = post("/api/v1/settlements")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "receivableId": "%s",
						  "paymentCurrency": "BRL"
						}
						""".formatted(receivableId));
		if (correlationId != null) {
			request.header("X-Correlation-Id", correlationId);
		}
		return mockMvc.perform(request);
	}

	private static String createdEvent(String output, String correlationId) {
		for (String line : output.split("\\R")) {
			if (!line.contains(correlationId) || !line.contains("\"result\"")) {
				continue;
			}
			int start = line.indexOf('{');
			if (start < 0) {
				continue;
			}
			String json = line.substring(start);
			String foundId = JsonPath.read(json, "$.correlationId");
			String result = JsonPath.read(json, "$.result");
			if (correlationId.equals(foundId) && "created".equals(result)) {
				return json;
			}
		}
		throw new AssertionError(output);
	}

	private record Cedente(String id, String nome, String documento) {
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
