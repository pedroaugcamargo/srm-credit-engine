package com.srmasset.creditengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class InitialSchemaSeedTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void seedsDemonstrationCedentesBaseRateAndFx() {
		assertEquals(List.of("7", "7", "7"), idVersions());
		assertEquals(List.of("DOC-DEMO-001", "DOC-DEMO-002", "DOC-DEMO-003"), documentos());
		assertEquals(List.of("Cedente Demonstração 1", "Cedente Demonstração 2", "Cedente Demonstração 3"), nomes());
		assertEquals("7", baseRateIdVersion());
		assertEquals("7", fxRateIdVersion());
		assertEquals(0, new BigDecimal("0.01").compareTo(baseRate()));
		assertEquals("2026-01-01 00:00:00", baseRateValidFrom());
		assertEquals(0, new BigDecimal("5.4321").compareTo(fxRate()));
		assertEquals("USD", fxBaseCurrency());
		assertEquals("BRL", fxQuoteCurrency());
		assertEquals("SEED", fxSource());
		assertEquals("2026-01-01 00:00:00", fxValidFrom());
	}

	private List<String> idVersions() {
		return jdbc.queryForList(
				"select substring(id::text from 15 for 1) from cedentes order by documento",
				String.class);
	}

	private List<String> documentos() {
		return jdbc.queryForList("select documento from cedentes order by documento", String.class);
	}

	private List<String> nomes() {
		return jdbc.queryForList("select nome from cedentes order by documento", String.class);
	}

	private String baseRateIdVersion() {
		return jdbc.queryForObject("select substring(id::text from 15 for 1) from base_rates", String.class);
	}

	private BigDecimal baseRate() {
		return jdbc.queryForObject("select rate from base_rates", BigDecimal.class);
	}

	private String baseRateValidFrom() {
		return jdbc.queryForObject(
				"select to_char(valid_from at time zone 'America/Sao_Paulo', 'YYYY-MM-DD HH24:MI:SS') from base_rates",
				String.class);
	}

	private String fxRateIdVersion() {
		return jdbc.queryForObject("select substring(id::text from 15 for 1) from fx_rates", String.class);
	}

	private BigDecimal fxRate() {
		return jdbc.queryForObject(
				"select rate from fx_rates where base_currency = ? and quote_currency = ?",
				BigDecimal.class,
				"USD",
				"BRL");
	}

	private String fxBaseCurrency() {
		return jdbc.queryForObject("select base_currency from fx_rates", String.class);
	}

	private String fxQuoteCurrency() {
		return jdbc.queryForObject("select quote_currency from fx_rates", String.class);
	}

	private String fxSource() {
		return jdbc.queryForObject("select source from fx_rates", String.class);
	}

	private String fxValidFrom() {
		return jdbc.queryForObject(
				"select to_char(valid_from at time zone 'America/Sao_Paulo', 'YYYY-MM-DD HH24:MI:SS') from fx_rates",
				String.class);
	}

}
