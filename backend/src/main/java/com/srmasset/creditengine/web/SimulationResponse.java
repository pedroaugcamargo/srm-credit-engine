package com.srmasset.creditengine.web;

import java.math.BigDecimal;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;
import com.srmasset.creditengine.domain.pricing.PricingResult;

public record SimulationResponse(String faceValue, String termMonths, String baseRate, String spread,
		String presentValueBrl, String discountBrl, PaymentCurrency paymentCurrency, String fxRate, String netAmount) {

	static SimulationResponse from(PricingResult result) {
		return new SimulationResponse(plain(result.faceValue()), plain(result.termMonths()), plain(result.baseRate()),
				plain(result.spread()), plain(result.presentValueBrl()), plain(result.discountBrl()),
				result.paymentCurrency(), result.fxRate() == null ? null : plain(result.fxRate()),
				plain(result.netAmount()));
	}

	private static String plain(BigDecimal value) {
		return value.toPlainString();
	}

}
