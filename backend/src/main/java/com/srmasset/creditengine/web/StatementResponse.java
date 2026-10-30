package com.srmasset.creditengine.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.srmasset.creditengine.application.StatementLine;
import com.srmasset.creditengine.application.StatementPage;
import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

public record StatementResponse(List<Item> items, int page, int size, long totalElements) {

	public record Item(
			UUID id,
			UUID cedenteId,
			String cedenteNome,
			UUID receivableId,
			PaymentCurrency paymentCurrency,
			String faceValue,
			String presentValueBrl,
			String fxRate,
			String netAmount,
			OffsetDateTime settledAt) {
	}

	static StatementResponse from(StatementPage statement) {
		List<Item> items = statement.items().stream().map(StatementResponse::item).toList();
		return new StatementResponse(items, statement.page(), statement.size(), statement.totalElements());
	}

	private static Item item(StatementLine line) {
		return new Item(
				line.id(),
				line.cedenteId(),
				line.cedenteNome(),
				line.receivableId(),
				line.paymentCurrency(),
				money(line.faceValue()),
				money(line.presentValueBrl()),
				line.fxRate() == null ? null : line.fxRate().stripTrailingZeros().toPlainString(),
				money(line.netAmount()),
				line.settledAt());
	}

	private static String money(BigDecimal value) {
		return value.toPlainString();
	}

}
