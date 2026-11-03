package com.srmasset.creditengine.web;

import java.math.BigDecimal;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;
import com.srmasset.creditengine.domain.pricing.PricingResult;

public record SimulationResponse(String faceValue, String termMonths, String baseRate, String spread,
		String presentValueBrl, String discountBrl, PaymentCurrency paymentCurrency, String fxRate, String netAmount) {

	static SimulationResponse from(PricingResult result) {
		return new SimulationResponse(money(result.faceValue()), plain(result.termMonths()), plain(result.baseRate()),
				plain(result.spread()), money(result.presentValueBrl()), money(result.discountBrl()),
				result.paymentCurrency(), result.fxRate() == null ? null : plain(result.fxRate()),
				money(result.netAmount()));
	}

	private static String money(BigDecimal value) {
		return value.toPlainString();
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

}
