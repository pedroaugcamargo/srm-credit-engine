package com.srmasset.creditengine.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.srmasset.creditengine.domain.fx.FxQuote;
import com.srmasset.creditengine.domain.fx.FxSelection;

public record CurrentFxRateResponse(
		UUID id,
		String base,
		String quote,
		String rate,
		OffsetDateTime validFrom,
		boolean stale) {

	static CurrentFxRateResponse from(FxSelection selection) {
		FxQuote quote = selection.quote();
		return new CurrentFxRateResponse(
				quote.id(),
				quote.baseCurrency(),
				quote.quoteCurrency(),
				plain(quote.rate()),
				FxRatePresentation.inSaoPaulo(quote.validFrom()),
				selection.stale());
	}

	private static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}

}
