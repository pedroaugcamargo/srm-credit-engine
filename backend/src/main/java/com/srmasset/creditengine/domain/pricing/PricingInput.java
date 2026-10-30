package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record PricingInput(
		BigDecimal faceValue,
		ReceivableType receivableType,
		BigDecimal termMonths,
		PaymentCurrency paymentCurrency) {

	public PricingInput {
		Objects.requireNonNull(faceValue, "faceValue");
		Objects.requireNonNull(receivableType, "receivableType");
		Objects.requireNonNull(termMonths, "termMonths");
		Objects.requireNonNull(paymentCurrency, "paymentCurrency");
	}

}
