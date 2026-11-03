package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.srmasset.creditengine.application.CedenteService;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedErrorProblemDetailTest {

	private static final String INTERNAL_MESSAGE = "detalhe interno que não pode vazar";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CedenteService cedentes;

	@Test
	void unexpectedErrorBecomesAnOpaque500AndIsLogged(CapturedOutput output) throws Exception {
		when(cedentes.list()).thenThrow(new IllegalStateException(INTERNAL_MESSAGE));

		String body = mockMvc.perform(get("/api/v1/cedentes")).andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("Erro inesperado. Tente novamente mais tarde.")).andReturn()
				.getResponse().getContentAsString();

		assertFalse(body.contains(INTERNAL_MESSAGE));
		assertTrue(output.getAll().contains(INTERNAL_MESSAGE));
	}

	@Test
	void malformedIdBecomesAValidationError() throws Exception {
		mockMvc.perform(get("/api/v1/settlements/nao-e-um-uuid")).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unknownRouteKeeps404AsProblemDetail() throws Exception {
		mockMvc.perform(get("/api/v1/rota-inexistente")).andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void unsupportedMethodKeeps405AsProblemDetail() throws Exception {
		mockMvc.perform(delete("/api/v1/cedentes")).andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

}
