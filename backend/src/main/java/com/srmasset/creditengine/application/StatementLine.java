package com.srmasset.creditengine.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

public record StatementLine(
		UUID id,
		UUID cedenteId,
		String cedenteNome,
		UUID receivableId,
		PaymentCurrency paymentCurrency,
		BigDecimal faceValue,
		BigDecimal presentValueBrl,
		BigDecimal fxRate,
		BigDecimal netAmount,
		OffsetDateTime settledAt) {
}
