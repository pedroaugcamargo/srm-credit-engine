package com.srmasset.creditengine.web;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FxRateRequest(@NotBlank(message = "Informe a moeda base.") String base,
		@NotBlank(message = "Informe a moeda de cotação.") String quote,
		@NotBlank(message = "Informe a taxa.") @DecimalMin(value = "0", inclusive = false, message = "A taxa precisa ser maior que zero.") String rate,
		@NotNull(message = "Informe a vigência.") OffsetDateTime validFrom) {
}
