package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "credit-engine.rounding")
public final class RoundingPolicy {

	private final int scale;
	private final RoundingMode mode;

	public RoundingPolicy(
			@DefaultValue("2") int scale,
			@DefaultValue("HALF_EVEN") RoundingMode mode) {
		this.scale = scale;
		this.mode = Objects.requireNonNull(mode, "mode");
	}

	public BigDecimal round(BigDecimal amount) {
		return Objects.requireNonNull(amount, "amount").setScale(scale, mode);
	}

	public int scale() {
		return scale;
	}

	public RoundingMode mode() {
		return mode;
	}

}
