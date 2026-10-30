package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record PricingResult(
		BigDecimal faceValue,
		BigDecimal termMonths,
		BigDecimal baseRate,
		BigDecimal spread,
		BigDecimal presentValueBrl,
		BigDecimal discountBrl,
		PaymentCurrency paymentCurrency,
		BigDecimal fxRate,
		BigDecimal netAmount) {

	public PricingResult {
		Objects.requireNonNull(faceValue, "faceValue");
		Objects.requireNonNull(termMonths, "termMonths");
		Objects.requireNonNull(baseRate, "baseRate");
		Objects.requireNonNull(spread, "spread");
		Objects.requireNonNull(presentValueBrl, "presentValueBrl");
		Objects.requireNonNull(discountBrl, "discountBrl");
		Objects.requireNonNull(paymentCurrency, "paymentCurrency");
		Objects.requireNonNull(netAmount, "netAmount");
	}

}
