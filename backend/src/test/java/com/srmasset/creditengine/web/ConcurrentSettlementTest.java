package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ConcurrentSettlementTest.OperationAtNoonOnNewYear.class)
class ConcurrentSettlementTest {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final String DUE_DATE = "2026-04-01";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	@Test
	void twoThreadsWithDifferentKeysLeaveOneSettlement() throws Exception {
		String receivableId = registerReceivable();
		Outcome first = null;
		Outcome second = null;
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Outcome> left = pool.submit(() -> settleAfter(ready, start, receivableId, "BRL", UUID.randomUUID()));
			Future<Outcome> right = pool
					.submit(() -> settleAfter(ready, start, receivableId, "BRL", UUID.randomUUID()));
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			first = left.get(30, TimeUnit.SECONDS);
			second = right.get(30, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		List<Integer> statuses = List.of(first.status(), second.status()).stream().sorted().toList();
		assertEquals(List.of(201, 409), statuses, first.body() + "\n" + second.body());
		Outcome created = first.status() == 201 ? first : second;
		Outcome rejected = first.status() == 409 ? first : second;
		assertGoldenBrl(created.body());
		assertEquals("RECEIVABLE_ALREADY_SETTLED", JsonPath.read(rejected.body(), "$.code"));
		assertEquals(1, settlementsOf(receivableId));
		assertEquals("SETTLED", receivableStatus(receivableId));
	}

	@Test
	void twoThreadsWithTheSameKeyLeaveOneSettlement() throws Exception {
		String receivableId = registerReceivable();
		UUID key = UUID.randomUUID();
		Outcome first;
		Outcome second;
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Outcome> left = pool.submit(() -> settleAfter(ready, start, receivableId, "BRL", key));
			Future<Outcome> right = pool.submit(() -> settleAfter(ready, start, receivableId, "BRL", key));
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			first = left.get(30, TimeUnit.SECONDS);
			second = right.get(30, TimeUnit.SECONDS);
		} finally {
			pool.shutdownNow();
		}

		List<Outcome> outcomes = List.of(first, second);
		long created = outcomes.stream().filter(outcome -> outcome.status() == 201).count();
		assertEquals(1, created, first.body() + "\n" + second.body());
		assertTrue(
				outcomes.stream().allMatch(
						outcome -> outcome.status() == 200 || outcome.status() == 201 || outcome.status() == 409),
				first.body() + "\n" + second.body());
		Outcome winner = outcomes.stream().filter(outcome -> outcome.status() == 201).findFirst().orElseThrow();
		assertGoldenBrl(winner.body());
		outcomes.stream().filter(outcome -> outcome.status() == 200).forEach(outcome -> {
			assertEquals("true", outcome.replayed());
			String winnerId = JsonPath.read(winner.body(), "$.id");
			String replayId = JsonPath.read(outcome.body(), "$.id");
			assertEquals(winnerId, replayId);
			assertGoldenBrl(outcome.body());
		});
		assertEquals(1, settlementsOf(receivableId));
		assertEquals(1, settlementsOfKey(key));
	}

	private void assertGoldenBrl(String body) {
		assertEquals("BRL", JsonPath.read(body, "$.paymentCurrency"));
		assertEquals("100000.00", JsonPath.read(body, "$.faceValue"));
		assertEquals("3", JsonPath.read(body, "$.termMonths"));
		assertEquals("0.01", JsonPath.read(body, "$.baseRate"));
		assertEquals("0.015", JsonPath.read(body, "$.spread"));
		assertEquals("92859.94", JsonPath.read(body, "$.presentValueBrl"));
		assertEquals("7140.06", JsonPath.read(body, "$.discountBrl"));
		assertNull(JsonPath.read(body, "$.fxRate"));
		assertEquals("92859.94", JsonPath.read(body, "$.netAmount"));
	}

	private String registerReceivable() throws Exception {
		String cedenteId = jdbc.queryForObject("select id::text from cedentes where documento = 'DOC-DEMO-001'",
				String.class);
		HttpResponse<String> response = http.send(request("/api/v1/receivables", null, """
				{
				  "cedenteId": "%s",
				  "type": "DUPLICATA_MERCANTIL",
				  "faceValue": "100000.00",
				  "dueDate": "%s"
				}
				""".formatted(cedenteId, DUE_DATE)), HttpResponse.BodyHandlers.ofString());
		assertEquals(201, response.statusCode(), response.body());
		return JsonPath.read(response.body(), "$.id");
	}

	private Outcome settleAfter(CountDownLatch ready, CountDownLatch start, String receivableId, String currency,
			UUID key) throws Exception {
		ready.countDown();
		assertTrue(start.await(10, TimeUnit.SECONDS));
		HttpResponse<String> response = http.send(request("/api/v1/settlements", key, """
				{
				  "receivableId": "%s",
				  "paymentCurrency": "%s"
				}
				""".formatted(receivableId, currency)), HttpResponse.BodyHandlers.ofString());
		return new Outcome(response.statusCode(), response.body(),
				response.headers().firstValue("Idempotent-Replayed").orElse(null));
	}

	private HttpRequest request(String path, UUID idempotencyKey, String body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body));
		if (idempotencyKey != null) {
			builder.header("Idempotency-Key", idempotencyKey.toString());
		}
		return builder.build();
	}

	private int settlementsOf(String receivableId) {
		return jdbc.queryForObject("select count(*) from settlements where receivable_id = ?::uuid", Integer.class,
				receivableId);
	}

	private int settlementsOfKey(UUID key) {
		return jdbc.queryForObject("select count(*) from settlements where idempotency_key = ?::uuid", Integer.class,
				key);
	}

	private String receivableStatus(String receivableId) {
		return jdbc.queryForObject("select status from receivables where id = ?::uuid", String.class, receivableId);
	}

	private record Outcome(int status, String body, String replayed) {
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
