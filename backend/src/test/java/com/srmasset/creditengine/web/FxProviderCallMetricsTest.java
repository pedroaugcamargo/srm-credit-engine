package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest(properties = "credit-engine.fx.provider.failure=SERVER_ERROR")
@AutoConfigureMockMvc
@Transactional
class FxProviderCallMetricsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MeterRegistry meters;

	@Test
	void countsEachRetriedServerErrorAsAProviderCall() throws Exception {
		double before = serverErrors();

		mockMvc.perform(post("/api/v1/fx-rates/refresh")).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("FX_PROVIDER_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Provedor de câmbio indisponível."));

		assertEquals(before + 3, serverErrors());
	}

	private double serverErrors() {
		Counter counter = meters.find("fx_provider_calls").tag("outcome", "server_error").counter();
		return counter == null ? 0 : counter.count();
	}

}
