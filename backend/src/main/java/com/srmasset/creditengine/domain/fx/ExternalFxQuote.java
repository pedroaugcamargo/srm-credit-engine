package com.srmasset.creditengine.domain.fx;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public record ExternalFxQuote(String base, String quote, BigDecimal rate, OffsetDateTime validFrom) {

	public ExternalFxQuote {
		Objects.requireNonNull(base, "base");
		Objects.requireNonNull(quote, "quote");
		Objects.requireNonNull(rate, "rate");
		Objects.requireNonNull(validFrom, "validFrom");
	}

}
