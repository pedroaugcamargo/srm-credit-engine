package com.srmasset.creditengine.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.srmasset.creditengine.application.ReceivableService;
import com.srmasset.creditengine.domain.receivable.Receivable;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/receivables")
public class ReceivableController {

	private final ReceivableService receivables;

	public ReceivableController(ReceivableService receivables) {
		this.receivables = receivables;
	}

	@PostMapping
	public ResponseEntity<ReceivableResponse> register(@Valid @RequestBody ReceivableRequest request) {
		Receivable receivable = receivables.register(
				request.cedenteId(),
				request.type(),
				request.faceValue(),
				request.dueDate());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(receivable.id())
				.toUri();
		return ResponseEntity.created(location).body(ReceivableResponse.from(receivable));
	}

	@GetMapping("/{id}")
	public ReceivableResponse get(@PathVariable UUID id) {
		return ReceivableResponse.from(receivables.get(id));
	}

}
