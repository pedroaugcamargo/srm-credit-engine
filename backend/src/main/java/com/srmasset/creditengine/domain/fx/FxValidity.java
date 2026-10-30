package com.srmasset.creditengine.domain.fx;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FxValidity {

	private final Duration maxAge;

	public FxValidity(Duration maxAge) {
		if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
			throw new IllegalArgumentException("maxAge");
		}
		this.maxAge = maxAge;
	}

	public FxSelection select(List<FxQuote> quotes, Instant at) {
		Objects.requireNonNull(quotes, "quotes");
		Objects.requireNonNull(at, "at");
		FxQuote vigente = quotes.stream()
				.filter(quote -> !quote.validFrom().toInstant().isAfter(at))
				.max(Comparator.comparing(quote -> quote.validFrom().toInstant()))
				.orElseThrow(FxRateUnavailableException::new);
		boolean stale = Duration.between(vigente.validFrom().toInstant(), at).compareTo(maxAge) > 0;
		return new FxSelection(vigente, stale);
	}

}
