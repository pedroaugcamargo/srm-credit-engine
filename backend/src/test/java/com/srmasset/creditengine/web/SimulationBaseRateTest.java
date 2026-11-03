package com.srmasset.creditengine.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(SimulationBaseRateTest.OperationIn2030.class)
class SimulationBaseRateTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final String NEW_RATE_VALID_FROM = "2030-01-01 00:00:00-03";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeNewRate() {
		jdbc.update("delete from base_rates where valid_from = ?::timestamptz", NEW_RATE_VALID_FROM);
	}

	@Test
	void simulatesWithTheBaseRateInForceInTheTable() throws Exception {
		jdbc.update("insert into base_rates (rate, valid_from) values (0.02, ?::timestamptz)", NEW_RATE_VALID_FROM);

		mockMvc.perform(post("/api/v1/pricing/simulations").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "faceValue": "100000.00",
				  "receivableType": "DUPLICATA_MERCANTIL",
				  "dueDate": "2030-05-30",
				  "paymentCurrency": "BRL"
				}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.termMonths").value("3"))
				.andExpect(jsonPath("$.baseRate").value("0.02")).andExpect(jsonPath("$.spread").value("0.015"))
				.andExpect(jsonPath("$.presentValueBrl").value("90194.27"))
				.andExpect(jsonPath("$.discountBrl").value("9805.73"));
	}

	@TestConfiguration
	static class OperationIn2030 {

		@Bean
		@Primary
		Clock operationIn2030Clock() {
			return Clock.fixed(LocalDate.of(2030, 3, 1).atStartOfDay(SAO_PAULO).toInstant(), SAO_PAULO);
		}

	}

}
