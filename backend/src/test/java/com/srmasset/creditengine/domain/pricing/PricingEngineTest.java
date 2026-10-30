package com.srmasset.creditengine.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.srmasset.creditengine.domain.pricing.strategy.ChequeStrategy;
import com.srmasset.creditengine.domain.pricing.strategy.DuplicataMercantilStrategy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class PricingEngineTest {

	private final PricingEngine engine = new PricingEngine(
			new PricingStrategyRegistry(List.of(new DuplicataMercantilStrategy(), new ChequeStrategy())),
			new RoundingPolicy(2, RoundingMode.HALF_EVEN),
			new BigDecimal("0.01"),
			() -> new BigDecimal("5.4321"),
			new SimpleMeterRegistry());

	@ParameterizedTest(name = "{0}")
	@MethodSource("goldenCases")
	void pricesToTheCentAndKeepsFaceEqualToPresentValuePlusDiscount(String name, PricingInput input, PricingResult expected) {
		PricingResult actual = engine.price(input);

		assertEquals(expected, actual);
		assertEquals(0, actual.faceValue().compareTo(actual.presentValueBrl().add(actual.discountBrl())));
	}

	@Test
	void pricesFractionalTermWithBigMath() {
		PricingResult actual = engine.price(
				input("100000.00", ReceivableType.DUPLICATA_MERCANTIL, "1.5", PaymentCurrency.BRL));

		assertEquals(result("100000.00", "1.5", "0.01", "0.015", "96363.86", "3636.14", PaymentCurrency.BRL, null, "96363.86"), actual);
		assertEquals(0, actual.faceValue().compareTo(actual.presentValueBrl().add(actual.discountBrl())));
	}

	static Stream<Arguments> goldenCases() {
		return Stream.of(
				Arguments.of("C1",
						input("100000.00", ReceivableType.DUPLICATA_MERCANTIL, "3", PaymentCurrency.BRL),
						result("100000.00", "3", "0.01", "0.015", "92859.94", "7140.06", PaymentCurrency.BRL, null, "92859.94")),
				Arguments.of("C2",
						input("100000.00", ReceivableType.CHEQUE, "3", PaymentCurrency.BRL),
						result("100000.00", "3", "0.01", "0.025", "90194.27", "9805.73", PaymentCurrency.BRL, null, "90194.27")),
				Arguments.of("C3",
						input("100000.00", ReceivableType.DUPLICATA_MERCANTIL, "3", PaymentCurrency.USD),
						result("100000.00", "3", "0.01", "0.015", "92859.94", "7140.06", PaymentCurrency.USD, "5.4321", "17094.67")));
	}

	private static PricingInput input(String faceValue, ReceivableType type, String termMonths, PaymentCurrency currency) {
		return new PricingInput(new BigDecimal(faceValue), type, new BigDecimal(termMonths), currency);
	}

	private static PricingResult result(
			String faceValue,
			String termMonths,
			String baseRate,
			String spread,
			String presentValueBrl,
			String discountBrl,
			PaymentCurrency currency,
			String fxRate,
			String netAmount) {
		return new PricingResult(
				new BigDecimal(faceValue),
				new BigDecimal(termMonths),
				new BigDecimal(baseRate),
				new BigDecimal(spread),
				new BigDecimal(presentValueBrl),
				new BigDecimal(discountBrl),
				currency,
				fxRate == null ? null : new BigDecimal(fxRate),
				new BigDecimal(netAmount));
	}

}
