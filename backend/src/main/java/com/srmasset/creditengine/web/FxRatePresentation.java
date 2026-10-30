package com.srmasset.creditengine.web;

import java.time.OffsetDateTime;
import java.time.ZoneId;

final class FxRatePresentation {

	private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

	private FxRatePresentation() {
	}

	static OffsetDateTime inSaoPaulo(OffsetDateTime validFrom) {
		return validFrom.atZoneSameInstant(SAO_PAULO).toOffsetDateTime();
	}

}
