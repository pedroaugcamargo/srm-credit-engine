package com.srmasset.creditengine.application;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;
import com.srmasset.creditengine.persistence.SettlementStatementRepository;

@Service
public class SettlementStatementService {

	private static final Duration MAX_PERIOD = Duration.ofDays(366);

	private static final int MAX_PAGE_SIZE = 100;

	private final SettlementStatementRepository statements;

	public SettlementStatementService(SettlementStatementRepository statements) {
		this.statements = statements;
	}

	public StatementPage statement(OffsetDateTime from, OffsetDateTime to, UUID cedenteId, PaymentCurrency currency,
			int page, int size) {
		if (from.isAfter(to)) {
			throw new StatementQueryException("O período está invertido.");
		}
		if (Duration.between(from, to).compareTo(MAX_PERIOD) > 0) {
			throw new StatementQueryException("O período não pode ser maior que 366 dias.");
		}
		if (page < 0) {
			throw new StatementQueryException("page não pode ser negativo.");
		}
		if (size < 1) {
			throw new StatementQueryException("size precisa ser pelo menos 1.");
		}
		if (size > MAX_PAGE_SIZE) {
			throw new StatementQueryException("size não pode ser maior que 100.");
		}
		return statements.find(from, to, cedenteId, currency, page, size);
	}

}
