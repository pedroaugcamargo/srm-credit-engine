package com.srmasset.creditengine.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.srmasset.creditengine.domain.pricing.ReceivableType;
import com.srmasset.creditengine.domain.receivable.Receivable;
import com.srmasset.creditengine.domain.receivable.ReceivableStatus;
import com.srmasset.creditengine.persistence.CedenteRepository;
import com.srmasset.creditengine.persistence.ReceivableEntity;
import com.srmasset.creditengine.persistence.ReceivableRepository;

@Service
public class ReceivableService {

	private final ReceivableRepository receivables;
	private final CedenteRepository cedentes;
	private final Clock clock;

	public ReceivableService(ReceivableRepository receivables, CedenteRepository cedentes, Clock clock) {
		this.receivables = receivables;
		this.cedentes = cedentes;
		this.clock = clock;
	}

	@Transactional
	public Receivable register(UUID cedenteId, ReceivableType type, String faceValue, LocalDate dueDate) {
		if (!cedentes.existsById(cedenteId)) {
			throw new CedenteNotFoundException();
		}
		ReceivableEntity receivable = new ReceivableEntity();
		receivable.setCedenteId(cedenteId);
		receivable.setType(type);
		receivable.setFaceValue(new BigDecimal(faceValue));
		receivable.setDueDate(dueDate);
		receivable.setStatus(ReceivableStatus.OPEN);
		receivable.setCreatedAt(OffsetDateTime.now(clock));
		return toReceivable(receivables.saveAndFlush(receivable));
	}

	@Transactional(readOnly = true)
	public Receivable get(UUID id) {
		return receivables.findById(id).map(ReceivableService::toReceivable)
				.orElseThrow(ReceivableNotFoundException::new);
	}

	private static Receivable toReceivable(ReceivableEntity receivable) {
		return new Receivable(receivable.getId(), receivable.getCedenteId(), receivable.getType(),
				receivable.getFaceValue(), receivable.getDueDate(), receivable.getStatus(), receivable.getVersion());
	}

}
