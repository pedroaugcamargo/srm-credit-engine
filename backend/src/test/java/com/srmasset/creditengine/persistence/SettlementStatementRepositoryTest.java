package com.srmasset.creditengine.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import com.srmasset.creditengine.application.StatementQueryTimeoutException;

@ExtendWith(MockitoExtension.class)
class SettlementStatementRepositoryTest {

	@Mock
	private NamedParameterJdbcTemplate jdbc;

	@Test
	void countTimeoutBecomesStatementQueryTimeout() {
		when(jdbc.execute(anyString(), any(SqlParameterSource.class), any(PreparedStatementCallback.class)))
				.thenThrow(new QueryTimeoutException("canceling statement due to user request"));
		SettlementStatementRepository statements = new SettlementStatementRepository(jdbc);

		StatementQueryTimeoutException failure = assertThrows(StatementQueryTimeoutException.class,
				() -> statements.find(OffsetDateTime.parse("2026-01-01T00:00:00-03:00"),
						OffsetDateTime.parse("2026-01-02T00:00:00-03:00"), null, null, 0, 20));

		assertEquals("A consulta do extrato excedeu o tempo limite.", failure.getMessage());
	}

}
