package com.srmasset.creditengine.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.srmasset.creditengine.domain.fx.FxQuote;

public record RegisteredFxRateResponse(UUID id, String base, String quote, String rate, OffsetDateTime validFrom) {

	static RegisteredFxRateResponse from(FxQuote quote) {
		return new RegisteredFxRateResponse(quote.id(), quote.baseCurrency(), quote.quoteCurrency(),
				plain(quote.rate()), FxRatePresentation.inSaoPaulo(quote.validFrom()));
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

}
