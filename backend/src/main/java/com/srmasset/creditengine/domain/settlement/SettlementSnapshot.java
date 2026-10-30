package com.srmasset.creditengine.domain.settlement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

public record SettlementSnapshot(UUID id, UUID receivableId, UUID cedenteId, UUID idempotencyKey, String requestHash,
		PaymentCurrency paymentCurrency, BigDecimal faceValue, BigDecimal termMonths, BigDecimal baseRate,
		BigDecimal spread, BigDecimal presentValueBrl, BigDecimal discountBrl, UUID fxRateId, BigDecimal fxRate,
		BigDecimal netAmount, OffsetDateTime settledAt) {
}
