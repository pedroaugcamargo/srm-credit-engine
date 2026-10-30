package com.srmasset.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public final class PricingStrategyRegistry {

	private final Map<ReceivableType, PricingStrategy> byType;

	public PricingStrategyRegistry(List<PricingStrategy> strategies) {
		Objects.requireNonNull(strategies, "strategies");
		Map<ReceivableType, PricingStrategy> indexed = new EnumMap<>(ReceivableType.class);
		for (PricingStrategy strategy : strategies) {
			Objects.requireNonNull(strategy, "strategy");
			ReceivableType type = Objects.requireNonNull(strategy.type(), "strategy type");
			if (indexed.putIfAbsent(type, strategy) != null) {
				throw new IllegalStateException("Duplicate pricing strategy for " + type);
			}
		}
		for (ReceivableType type : ReceivableType.values()) {
			if (!indexed.containsKey(type)) {
				throw new IllegalStateException("Missing pricing strategy for " + type);
			}
		}
		this.byType = Map.copyOf(indexed);
	}

	public BigDecimal monthlySpread(ReceivableType type, PricingContext context) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(context, "context");
		return byType.get(type).monthlySpread(context);
	}

}
