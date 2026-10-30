package com.srmasset.creditengine.web;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.srmasset.creditengine.application.IdempotencyKeyRequiredException;
import com.srmasset.creditengine.application.SettlementResult;
import com.srmasset.creditengine.application.SettlementStatementService;
import com.srmasset.creditengine.application.SettlementService;
import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

	private final SettlementService settlements;

	private final SettlementStatementService statement;

	public SettlementController(SettlementService settlements, SettlementStatementService statement) {
		this.settlements = settlements;
		this.statement = statement;
	}

	@GetMapping
	public StatementResponse statement(@RequestParam OffsetDateTime from, @RequestParam OffsetDateTime to,
			@RequestParam(required = false) UUID cedenteId, @RequestParam(required = false) PaymentCurrency currency,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return StatementResponse.from(statement.statement(from, to, cedenteId, currency, page, size));
	}

	@PostMapping
	public ResponseEntity<SettlementResponse> settle(
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody SettlementRequest request) {
		SettlementResult result = settlements.settle(requireKey(idempotencyKey), request.receivableId(),
				request.paymentCurrency());
		SettlementResponse body = SettlementResponse.from(result.snapshot());
		if (result.replayed()) {
			return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
		}
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(result.snapshot().id()).toUri();
		return ResponseEntity.created(location).body(body);
	}

	private static UUID requireKey(String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IdempotencyKeyRequiredException();
		}
		try {
			return UUID.fromString(idempotencyKey);
		} catch (IllegalArgumentException exception) {
			throw new IdempotencyKeyRequiredException();
		}
	}

}
