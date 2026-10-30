package com.srmasset.creditengine.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.srmasset.creditengine.domain.pricing.ReceivableType;
import com.srmasset.creditengine.domain.receivable.Receivable;
import com.srmasset.creditengine.domain.receivable.ReceivableStatus;

public record ReceivableResponse(UUID id, UUID cedenteId, ReceivableType type, String faceValue, LocalDate dueDate,
		ReceivableStatus status, long version) {

	static ReceivableResponse from(Receivable receivable) {
		return new ReceivableResponse(receivable.id(), receivable.cedenteId(), receivable.type(),
				plain(receivable.faceValue()), receivable.dueDate(), receivable.status(), receivable.version());
	}

	private static String plain(BigDecimal value) {
		return value.toPlainString();
	}

}
