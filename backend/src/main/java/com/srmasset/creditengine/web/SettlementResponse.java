package com.srmasset.creditengine.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;
import com.srmasset.creditengine.domain.settlement.SettlementSnapshot;

public record SettlementResponse(UUID id, UUID receivableId, UUID cedenteId, UUID idempotencyKey,
		PaymentCurrency paymentCurrency, String faceValue, String termMonths, String baseRate, String spread,
		String presentValueBrl, String discountBrl, UUID fxRateId, String fxRate, String netAmount,
		OffsetDateTime settledAt) {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	static SettlementResponse from(SettlementSnapshot snapshot) {
		return new SettlementResponse(snapshot.id(), snapshot.receivableId(), snapshot.cedenteId(),
				snapshot.idempotencyKey(), snapshot.paymentCurrency(), money(snapshot.faceValue()),
				plain(snapshot.termMonths()), plain(snapshot.baseRate()), plain(snapshot.spread()),
				money(snapshot.presentValueBrl()), money(snapshot.discountBrl()), snapshot.fxRateId(),
				snapshot.fxRate() == null ? null : plain(snapshot.fxRate()), money(snapshot.netAmount()),
				snapshot.settledAt().atZoneSameInstant(SAO_PAULO).toOffsetDateTime());
	}

	private static String money(BigDecimal value) {
		return value.toPlainString();
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

}
