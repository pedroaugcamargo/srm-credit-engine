package com.srmasset.creditengine.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.srmasset.creditengine.application.StatementLine;
import com.srmasset.creditengine.application.StatementPage;
import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

@Repository
public class SettlementStatementRepository {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

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

		Long total = jdbc.queryForObject("select count(*) from settlements s" + where, params, Long.class);
		params.addValue("limit", size);
		params.addValue("offset", page * size);
		List<StatementLine> items = jdbc.query("""
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
		return new StatementPage(items, page, size, total == null ? 0 : total);
	}

	private static OffsetDateTime settledAt(OffsetDateTime stored) {
		return stored.atZoneSameInstant(SAO_PAULO).toOffsetDateTime();
	}

}
