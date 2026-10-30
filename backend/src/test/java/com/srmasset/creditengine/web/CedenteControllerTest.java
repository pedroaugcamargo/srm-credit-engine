package com.srmasset.creditengine.web;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CedenteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void listsCedentesOrderedByName() throws Exception {
		jdbc.update("insert into cedentes (nome, documento) values (?, ?)", "Alfa Cedente", "DOC-TEST-ALFA");
		String alfaId = jdbc.queryForObject("select id::text from cedentes where documento = ?", String.class,
				"DOC-TEST-ALFA");

		mockMvc.perform(get("/api/v1/cedentes")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].id").value(alfaId)).andExpect(jsonPath("$[0].nome").value("Alfa Cedente"))
				.andExpect(jsonPath("$[1].nome").value("Cedente Demonstração 1"))
				.andExpect(jsonPath("$[3].nome").value("Cedente Demonstração 3"));
	}

	@Test
	void doesNotExposeTheCedenteDocument() throws Exception {
		mockMvc.perform(get("/api/v1/cedentes")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0]", not(hasKey("documento"))));
	}

}
