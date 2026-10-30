package com.srmasset.creditengine.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PricingSimulationControllerTest.FixedOperationDate.class)
class PricingSimulationControllerTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void simulatesCaseC1() throws Exception {
		mockMvc.perform(post("/api/v1/pricing/simulations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "faceValue": "100000.00",
						  "receivableType": "DUPLICATA_MERCANTIL",
						  "dueDate": "2027-01-24",
						  "paymentCurrency": "BRL"
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "faceValue": "100000.00",
						  "termMonths": "3",
						  "baseRate": "0.01",
						  "spread": "0.015",
						  "presentValueBrl": "92859.94",
						  "discountBrl": "7140.06",
						  "paymentCurrency": "BRL",
						  "fxRate": null,
						  "netAmount": "92859.94"
						}
						"""));
	}

	@Test
	void rejectsNonPositiveFaceValue() throws Exception {
		mockMvc.perform(post("/api/v1/pricing/simulations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "faceValue": "0.00",
						  "receivableType": "DUPLICATA_MERCANTIL",
						  "dueDate": "2027-01-24",
						  "paymentCurrency": "BRL"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsDueDateOnTheOperationDate() throws Exception {
		mockMvc.perform(post("/api/v1/pricing/simulations")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "faceValue": "100000.00",
						  "receivableType": "DUPLICATA_MERCANTIL",
						  "dueDate": "2026-10-26",
						  "paymentCurrency": "BRL"
						}
						"""))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_TERM"));
	}

	@TestConfiguration
	static class FixedOperationDate {

		@Bean
		@Primary
		Clock fixedOperationClock() {
			return Clock.fixed(LocalDate.of(2026, 10, 26).atStartOfDay(SAO_PAULO).toInstant(), SAO_PAULO);
		}

	}

}
