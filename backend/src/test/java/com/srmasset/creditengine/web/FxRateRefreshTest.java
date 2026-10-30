package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.srmasset.creditengine.application.FxRateRefreshScheduler;

@SpringBootTest(properties = {
		"credit-engine.fx.provider.failure=NONE",
		"credit-engine.fx.provider.rate=5.6",
		"credit-engine.fx.provider.valid-from=2026-10-29T10:00:00-03:00"
})
@AutoConfigureMockMvc
@Transactional
class FxRateRefreshTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectProvider<FxRateRefreshScheduler> scheduler;

	@Test
	void refreshInsertsANewRateAndLeavesTheSeedUntouched() throws Exception {
		assertNull(scheduler.getIfAvailable());
		assertEquals(0, new BigDecimal("5.4321").compareTo(rateOf("SEED")));

		mockMvc.perform(post("/api/v1/fx-rates/refresh"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.base").value("USD"))
				.andExpect(jsonPath("$.quote").value("BRL"))
				.andExpect(jsonPath("$.rate").value("5.6"))
				.andExpect(jsonPath("$.validFrom").value("2026-10-29T10:00:00-03:00"));

		assertEquals(0, new BigDecimal("5.6").compareTo(rateOf("EXTERNAL")));
		assertEquals(0, new BigDecimal("5.4321").compareTo(rateOf("SEED")));
		assertEquals(1, countOf("EXTERNAL"));
		assertEquals(1, countOf("SEED"));
	}

	private BigDecimal rateOf(String source) {
		return jdbc.queryForObject(
				"select rate from fx_rates where source = ?",
				BigDecimal.class,
				source);
	}

	private int countOf(String source) {
		return jdbc.queryForObject(
				"select count(*) from fx_rates where source = ?",
				Integer.class,
				source);
	}

}
