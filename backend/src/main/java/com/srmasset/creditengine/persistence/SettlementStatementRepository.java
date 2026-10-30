package com.srmasset.creditengine.persistence;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import com.srmasset.creditengine.application.StatementLine;
import com.srmasset.creditengine.application.StatementPage;
import com.srmasset.creditengine.application.StatementQueryTimeoutException;
import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

@Repository
public class SettlementStatementRepository {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private static final int QUERY_TIMEOUT_SECONDS = 2;

	private final NamedParameterJdbcTemplate jdbc;

	public SettlementStatementRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public StatementPage find(OffsetDateTime from, OffsetDateTime to, UUID cedenteId, PaymentCurrency currency,
			int page, int size) {
		StringBuilder where = new StringBuilder("""
				 where s.settled_at >= :from
				   and s.settled_at <= :to
				""");
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("from", from).addValue("to", to);
		if (cedenteId != null) {
			where.append(" and s.cedente_id = :cedenteId");
			params.addValue("cedenteId", cedenteId);
		}
		if (currency != null) {
			where.append(" and s.payment_currency = :currency");
			params.addValue("currency", currency.name());
		}

		long total = count("select count(*) from settlements s" + where, params);
		params.addValue("limit", size);
		params.addValue("offset", (long) page * size);
		List<StatementLine> items = query("""
				select s.id, s.cedente_id, c.nome, s.receivable_id, s.payment_currency,
				       s.face_value, s.present_value_brl, s.fx_rate, s.net_amount, s.settled_at
				from settlements s
				join cedentes c on c.id = s.cedente_id
				""" + where + """
				 order by s.settled_at desc, s.id desc
				 limit :limit offset :offset
				""", params,
				(rs, row) -> new StatementLine(rs.getObject("id", UUID.class), rs.getObject("cedente_id", UUID.class),
						rs.getString("nome"), rs.getObject("receivable_id", UUID.class),
						PaymentCurrency.valueOf(rs.getString("payment_currency")), rs.getBigDecimal("face_value"),
						rs.getBigDecimal("present_value_brl"), rs.getBigDecimal("fx_rate"),
						rs.getBigDecimal("net_amount"), settledAt(rs.getObject("settled_at", OffsetDateTime.class))));
		return new StatementPage(items, page, size, total);
	}

	private long count(String sql, MapSqlParameterSource params) {
		List<Long> totals = query(sql, params, (rs, row) -> rs.getLong(1));
		return totals == null || totals.isEmpty() || totals.get(0) == null ? 0 : totals.get(0);
	}

	private <T> List<T> query(String sql, SqlParameterSource params, RowMapper<T> mapper) {
		try {
			return jdbc.execute(sql, params, (PreparedStatementCallback<List<T>>) statement -> {
				statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
				try (ResultSet result = statement.executeQuery()) {
					List<T> rows = new ArrayList<>();
					int row = 0;
					while (result.next()) {
						rows.add(mapper.mapRow(result, row++));
					}
					return rows;
				}
			});
		} catch (QueryTimeoutException exception) {
			throw new StatementQueryTimeoutException(exception);
		}
	}

	private static OffsetDateTime settledAt(OffsetDateTime stored) {
		return stored.atZoneSameInstant(SAO_PAULO).toOffsetDateTime();
	}

}
