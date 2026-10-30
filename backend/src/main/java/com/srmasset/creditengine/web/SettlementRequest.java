package com.srmasset.creditengine.web;

import java.util.UUID;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

import jakarta.validation.constraints.NotNull;

public record SettlementRequest(@NotNull(message = "Informe o recebível.") UUID receivableId,
		@NotNull(message = "Informe a moeda de pagamento.") PaymentCurrency paymentCurrency) {
}
