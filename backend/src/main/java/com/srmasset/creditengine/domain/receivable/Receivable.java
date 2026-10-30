package com.srmasset.creditengine.domain.receivable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.srmasset.creditengine.domain.pricing.ReceivableType;

public record Receivable(
		UUID id,
		UUID cedenteId,
		ReceivableType type,
		BigDecimal faceValue,
		LocalDate dueDate,
		ReceivableStatus status,
		long version) {
}
