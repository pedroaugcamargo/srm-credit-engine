package com.srmasset.creditengine.domain.fx;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FxQuote(UUID id, String baseCurrency, String quoteCurrency, BigDecimal rate, OffsetDateTime validFrom) {
}
