package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Arrays;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Tag("manual")
class SettlementStatementLoadTest {

	private static final int ROWS = 100_000;

	private static final int WARMUP = 20;

	private static final int SAMPLES = 100;

	private static final int P95_INDEX = 94;

	private static final long P95_LIMIT_NANOS = 500_000_000L;

	private static final String FROM = "2026-01-01T00:00:00-03:00";

	private static final String TO = "2026-01-03T00:00:00-03:00";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void firstPageStaysUnder500MillisecondsWithOneHundredThousandRows() throws Exception {
		insertRows();
		jdbc.execute("analyze receivables");
		jdbc.execute("analyze settlements");
		assertEquals(ROWS, rowsInWindow());

		for (int i = 0; i < WARMUP; i++) {
			assertPage(firstPage());
		}
		long[] elapsed = new long[SAMPLES];
		MvcResult last = null;
		for (int i = 0; i < SAMPLES; i++) {
			long start = System.nanoTime();
			last = firstPage();
			elapsed[i] = System.nanoTime() - start;
			assertEquals(200, last.getResponse().getStatus());
		}
		assertPage(last);
		Arrays.sort(elapsed);
		long p95 = elapsed[P95_INDEX];
		long min = elapsed[0];
		long max = elapsed[SAMPLES - 1];
		System.out.println("statement first page samples=" + SAMPLES + " minMs=" + millis(min) + " p95Ms=" + millis(p95)
				+ " maxMs=" + millis(max));
		assertTrue(p95 < P95_LIMIT_NANOS, "p95 da primeira página foi " + millis(p95) + " ms; o limite é 500 ms");
	}

	private void insertRows() {
		jdbc.update("""
				insert into receivables (cedente_id, type, face_value, due_date, status)
				select c.id, 'DUPLICATA_MERCANTIL', 100000.00, date '2026-04-01', 'SETTLED'
				from cedentes c
				cross join generate_series(1, 100000)
				where c.documento = 'DOC-DEMO-001'
				""");
		jdbc.update("""
				insert into settlements (
				    receivable_id, cedente_id, idempotency_key, request_hash,
				    payment_currency, face_value, term_months, base_rate, spread,
				    present_value_brl, discount_brl, fx_rate_id, fx_rate, net_amount, settled_at)
				select r.id,
				       r.cedente_id,
				       uuidv7(),
				       'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
				       case when r.n % 2 = 0 then 'BRL' else 'USD' end,
				       100000.00,
				       3,
				       0.01,
				       0.015,
				       92859.94,
				       7140.06,
				       case when r.n % 2 = 0 then null else fx.id end,
				       case when r.n % 2 = 0 then null else fx.rate end,
				       case when r.n % 2 = 0 then 92859.94 else 17094.67 end,
				       timestamptz '2026-01-01 00:00:00-03' + (r.n * interval '1 second')
				from (
				    select id, cedente_id, row_number() over (order by id) as n
				    from receivables
				    where cedente_id = (select id from cedentes where documento = 'DOC-DEMO-001')
				      and status = 'SETTLED'
				) r
				join fx_rates fx on fx.source = 'SEED'
				""");
	}

	private int rowsInWindow() {
		Integer count = jdbc.queryForObject("""
				select count(*) from settlements
				where settled_at >= timestamptz '2026-01-01 00:00:00-03'
				  and settled_at <= timestamptz '2026-01-03 00:00:00-03'
				""", Integer.class);
		return count == null ? 0 : count;
	}

	private MvcResult firstPage() throws Exception {
		return mockMvc.perform(
				get("/api/v1/settlements").param("from", FROM).param("to", TO).param("page", "0").param("size", "20"))
				.andReturn();
	}

	private static void assertPage(MvcResult result) throws Exception {
		assertEquals(200, result.getResponse().getStatus());
		String body = result.getResponse().getContentAsString();
		assertEquals(0, number(JsonPath.read(body, "$.page")));
		assertEquals(20, number(JsonPath.read(body, "$.size")));
		assertEquals(ROWS, number(JsonPath.read(body, "$.totalElements")));
		assertEquals(20, number(JsonPath.read(body, "$.items.length()")));
	}

	private static int number(Object value) {
		return ((Number) value).intValue();
	}

	private static long millis(long nanos) {
		return nanos / 1_000_000L;
	}

}
