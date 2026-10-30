package com.srmasset.creditengine.web;

import java.time.LocalDate;
import java.util.UUID;

import com.srmasset.creditengine.domain.pricing.ReceivableType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReceivableRequest(
		@NotNull(message = "Informe o cedente.")
		UUID cedenteId,
		@NotNull(message = "Informe o tipo do recebível.")
		ReceivableType type,
		@NotBlank(message = "Informe o valor de face.")
		@DecimalMin(value = "0", inclusive = false, message = "O valor de face precisa ser maior que zero.")
		String faceValue,
		@NotNull(message = "Informe o vencimento.")
		LocalDate dueDate) {
}
