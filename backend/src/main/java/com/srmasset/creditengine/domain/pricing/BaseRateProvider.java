package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.time.Instant;

public interface BaseRateProvider {

	BigDecimal rateAt(Instant at);

}
