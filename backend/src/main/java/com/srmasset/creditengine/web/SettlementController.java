package com.srmasset.creditengine.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.srmasset.creditengine.application.IdempotencyKeyRequiredException;
import com.srmasset.creditengine.application.SettlementService;
import com.srmasset.creditengine.domain.settlement.SettlementSnapshot;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

	private final SettlementService settlements;

	public SettlementController(SettlementService settlements) {
		this.settlements = settlements;
	}

	@PostMapping
	public ResponseEntity<SettlementResponse> settle(
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody SettlementRequest request) {
		SettlementSnapshot snapshot = settlements.settle(
				requireKey(idempotencyKey),
				request.receivableId(),
				request.paymentCurrency());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(snapshot.id())
				.toUri();
		return ResponseEntity.created(location).body(SettlementResponse.from(snapshot));
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
