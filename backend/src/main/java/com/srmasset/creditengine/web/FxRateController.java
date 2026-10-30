package com.srmasset.creditengine.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.srmasset.creditengine.application.FxRateService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/fx-rates")
public class FxRateController {

	private final FxRateService rates;

	public FxRateController(FxRateService rates) {
		this.rates = rates;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegisteredFxRateResponse register(@Valid @RequestBody FxRateRequest request) {
		return RegisteredFxRateResponse.from(rates.register(request.base(), request.quote(), request.rate(), request.validFrom()));
	}

	@GetMapping("/current")
	public CurrentFxRateResponse current(@RequestParam String base, @RequestParam String quote) {
		return CurrentFxRateResponse.from(rates.current(base, quote));
	}

}
