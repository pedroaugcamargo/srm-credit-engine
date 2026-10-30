package com.srmasset.creditengine.web;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.srmasset.creditengine.domain.pricing.PricingEngine;
import com.srmasset.creditengine.domain.pricing.PricingInput;
import com.srmasset.creditengine.domain.pricing.PricingResult;
import com.srmasset.creditengine.domain.pricing.TermCalculator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/pricing/simulations")
public class PricingSimulationController {

	private static final String REQUEST_EXAMPLE = """
			{
			  "faceValue": "100000.00",
			  "receivableType": "DUPLICATA_MERCANTIL",
			  "dueDate": "2027-01-24",
			  "paymentCurrency": "USD"
			}
			""";

	private static final String RESPONSE_EXAMPLE = """
			{
			  "faceValue": "100000.00",
			  "termMonths": "3",
			  "baseRate": "0.01",
			  "spread": "0.015",
			  "presentValueBrl": "92859.94",
			  "discountBrl": "7140.06",
			  "paymentCurrency": "USD",
			  "fxRate": "5.4321",
			  "netAmount": "17094.67"
			}
			""";

	private final PricingEngine engine;
	private final TermCalculator termCalculator;
	private final Clock clock;

	public PricingSimulationController(PricingEngine engine, TermCalculator termCalculator, Clock clock) {
		this.engine = engine;
		this.termCalculator = termCalculator;
		this.clock = clock;
	}

	@PostMapping
	@Operation(
			summary = "Simula o valor líquido de um recebível",
			requestBody = @RequestBody(content = @Content(examples = @ExampleObject(name = "duplicataUsd", value = REQUEST_EXAMPLE))),
			responses = {
					@ApiResponse(responseCode = "200", content = @Content(examples = @ExampleObject(name = "duplicataUsd", value = RESPONSE_EXAMPLE))),
					@ApiResponse(responseCode = "400", description = "VALIDATION_ERROR"),
					@ApiResponse(responseCode = "422", description = "INVALID_TERM")
			})
	public SimulationResponse simulate(@Valid @org.springframework.web.bind.annotation.RequestBody SimulationRequest request) {
		LocalDate operationDate = LocalDate.now(clock);
		BigDecimal termMonths = termCalculator.termMonths(operationDate, request.dueDate());
		PricingResult result = engine.price(new PricingInput(
				new BigDecimal(request.faceValue()),
				request.receivableType(),
				termMonths,
				request.paymentCurrency()));
		return SimulationResponse.from(result);
	}

}
