package com.srmasset.creditengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SettlementImmutabilityTest {

	private static final BigDecimal NET_AMOUNT = new BigDecimal("92859.94");

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void rejectsUpdateAndDeleteOfASettlement() {
		String cedenteId = jdbc.queryForObject(
				"select id::text from cedentes where documento = ?",
				String.class,
				"DOC-DEMO-001");
		String receivableId = jdbc.queryForObject("""
				insert into receivables (cedente_id, type, face_value, due_date)
				values (?::uuid, 'DUPLICATA_MERCANTIL', 100000.00, date '2027-01-24')
				returning id::text
				""", String.class, cedenteId);
		String settlementId = jdbc.queryForObject("""
				insert into settlements (
				    receivable_id, cedente_id, idempotency_key, request_hash, payment_currency,
				    face_value, term_months, base_rate, spread, present_value_brl, discount_brl, net_amount, settled_at)
				values (?::uuid, ?::uuid, ?::uuid, ?, 'BRL', 100000.00, 3, 0.01, 0.015, 92859.94, 7140.06, 92859.94, timestamptz '2026-01-01 12:00:00-03')
				returning id::text
				""", String.class, receivableId, cedenteId, UUID.randomUUID(), "a".repeat(64));

		DataAccessException updateFailure = assertThrows(DataAccessException.class, () -> jdbc.update(
				"update settlements set net_amount = ? where id = ?::uuid",
				new BigDecimal("1.00"),
				settlementId));
		assertTrue(updateFailure.getMostSpecificCause().getMessage().contains("settlements are immutable"));
		assertEquals(0, NET_AMOUNT.compareTo(netAmount(settlementId)));

		DataAccessException deleteFailure = assertThrows(DataAccessException.class, () -> jdbc.update(
				"delete from settlements where id = ?::uuid",
				settlementId));
		assertTrue(deleteFailure.getMostSpecificCause().getMessage().contains("settlements are immutable"));
		assertEquals(1, count(settlementId));
		assertEquals(0, NET_AMOUNT.compareTo(netAmount(settlementId)));
	}

	private BigDecimal netAmount(String settlementId) {
		return jdbc.queryForObject(
				"select net_amount from settlements where id = ?::uuid",
				BigDecimal.class,
				settlementId);
	}

	private int count(String settlementId) {
		Integer count = jdbc.queryForObject(
				"select count(*) from settlements where id = ?::uuid",
				Integer.class,
				settlementId);
		return count;
	}

}
