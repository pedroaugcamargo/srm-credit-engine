package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReceivableControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void registersAnOpenReceivableAndReturnsIt() throws Exception {
		String cedenteId = cedenteId("DOC-DEMO-001");

		MvcResult created = mockMvc.perform(post("/api/v1/receivables")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "cedenteId": "%s",
						  "type": "DUPLICATA_MERCANTIL",
						  "faceValue": "100000.00",
						  "dueDate": "2027-01-24"
						}
						""".formatted(cedenteId)))
				.andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.LOCATION))
				.andExpect(jsonPath("$.cedenteId").value(cedenteId))
				.andExpect(jsonPath("$.type").value("DUPLICATA_MERCANTIL"))
				.andExpect(jsonPath("$.faceValue").value("100000.00"))
				.andExpect(jsonPath("$.dueDate").value("2027-01-24"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.version").value(0))
				.andReturn();

		String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		assertNotNull(id);
		String location = created.getResponse().getHeader(HttpHeaders.LOCATION);
		assertNotNull(location);
		assertTrue(location.endsWith("/api/v1/receivables/" + id));

		mockMvc.perform(get("/api/v1/receivables/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.cedenteId").value(cedenteId))
				.andExpect(jsonPath("$.type").value("DUPLICATA_MERCANTIL"))
				.andExpect(jsonPath("$.faceValue").value("100000.00"))
				.andExpect(jsonPath("$.dueDate").value("2027-01-24"))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void rejectsAFaceValueThatIsNotPositive() throws Exception {
		String cedenteId = cedenteId("DOC-DEMO-001");

		mockMvc.perform(post("/api/v1/receivables")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "cedenteId": "%s",
						  "type": "DUPLICATA_MERCANTIL",
						  "faceValue": "0.00",
						  "dueDate": "2027-01-24"
						}
						""".formatted(cedenteId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsAnUnknownCedente() throws Exception {
		mockMvc.perform(post("/api/v1/receivables")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "cedenteId": "%s",
						  "type": "CHEQUE",
						  "faceValue": "100000.00",
						  "dueDate": "2027-01-24"
						}
						""".formatted(UUID.randomUUID())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CEDENTE_NOT_FOUND"));
	}

	@Test
	void returnsNotFoundForAnUnknownReceivable() throws Exception {
		mockMvc.perform(get("/api/v1/receivables/" + UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RECEIVABLE_NOT_FOUND"));
	}

	private String cedenteId(String documento) {
		return jdbc.queryForObject(
				"select id::text from cedentes where documento = ?",
				String.class,
				documento);
	}

}
