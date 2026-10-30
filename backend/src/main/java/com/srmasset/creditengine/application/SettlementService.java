package com.srmasset.creditengine.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.srmasset.creditengine.domain.fx.FxRateStaleException;
import com.srmasset.creditengine.domain.fx.FxSelection;
import com.srmasset.creditengine.domain.pricing.PaymentCurrency;
import com.srmasset.creditengine.domain.pricing.PricingEngine;
import com.srmasset.creditengine.domain.pricing.PricingInput;
import com.srmasset.creditengine.domain.pricing.PricingResult;
import com.srmasset.creditengine.domain.pricing.TermCalculator;
import com.srmasset.creditengine.domain.receivable.ReceivableStatus;
import com.srmasset.creditengine.domain.settlement.SettlementSnapshot;
import com.srmasset.creditengine.persistence.DbBaseRateProvider;
import com.srmasset.creditengine.persistence.DbFxRateProvider;
import com.srmasset.creditengine.persistence.ReceivableEntity;
import com.srmasset.creditengine.persistence.ReceivableRepository;
import com.srmasset.creditengine.persistence.SettlementEntity;
import com.srmasset.creditengine.persistence.SettlementRepository;

@Service
public class SettlementService {

	private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

	private final ReceivableRepository receivables;
	private final SettlementRepository settlements;
	private final DbBaseRateProvider baseRates;
	private final DbFxRateProvider fxRates;
	private final PricingEngine engine;
	private final TermCalculator termCalculator;
	private final Clock clock;
	private final TransactionTemplate transaction;
	private final TransactionTemplate requiresNew;

	public SettlementService(
			ReceivableRepository receivables,
			SettlementRepository settlements,
			DbBaseRateProvider baseRates,
			DbFxRateProvider fxRates,
			PricingEngine engine,
			TermCalculator termCalculator,
			Clock clock,
			PlatformTransactionManager transactions) {
		this.receivables = receivables;
		this.settlements = settlements;
		this.baseRates = baseRates;
		this.fxRates = fxRates;
		this.engine = engine;
		this.termCalculator = termCalculator;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactions);
		this.requiresNew = new TransactionTemplate(transactions);
		this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	public SettlementResult settle(UUID idempotencyKey, UUID receivableId, PaymentCurrency paymentCurrency) {
		String hash = requestHash(receivableId, paymentCurrency);
		Optional<SettlementEntity> existing = settlements.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			if (!sameHash(existing.get(), hash)) {
				logEvent("rejected", null, receivableId, null);
				throw new IdempotencyKeyReusedException();
			}
			return logged("replayed", toSnapshot(existing.get()));
		}
		try {
			SettlementSnapshot created = transaction.execute(status -> create(idempotencyKey, receivableId, paymentCurrency, hash));
			return logged("created", created);
		} catch (DataIntegrityViolationException exception) {
			if (causedBy(exception, "settlements_idempotency_key_unique")) {
				SettlementEntity original = requiresNew.execute(status -> settlements.findByIdempotencyKey(idempotencyKey)
						.orElseThrow(() -> exception));
				return logged("replayed", toSnapshot(original));
			}
			if (causedBy(exception, "settlements_receivable_unique")) {
				logEvent("conflict", null, receivableId, null);
				throw new ReceivableAlreadySettledException();
			}
			throw exception;
		} catch (ObjectOptimisticLockingFailureException exception) {
			logEvent("conflict", null, receivableId, null);
			throw new ReceivableAlreadySettledException();
		}
	}

	private SettlementSnapshot create(UUID idempotencyKey, UUID receivableId, PaymentCurrency paymentCurrency, String hash) {
		ReceivableEntity receivable = receivables.findById(receivableId).orElseThrow(() -> {
			logEvent("rejected", null, receivableId, null);
			return new ReceivableNotFoundException();
		});
		if (receivable.getStatus() == ReceivableStatus.SETTLED) {
			logEvent("conflict", null, receivableId, receivable.getCedenteId());
			throw new ReceivableAlreadySettledException();
		}

		Instant at = clock.instant();
		LocalDate operationDate = LocalDate.now(clock);
		BigDecimal termMonths = termCalculator.termMonths(operationDate, receivable.getDueDate());

		UUID fxRateId = null;
		BigDecimal fxRate = null;
		if (paymentCurrency == PaymentCurrency.USD) {
			FxSelection selection = fxRates.select("USD", "BRL", at);
			if (selection.stale()) {
				logEvent("rejected", null, receivableId, receivable.getCedenteId());
				throw new FxRateStaleException();
			}
			fxRateId = selection.quote().id();
			fxRate = selection.quote().rate();
		}
		BigDecimal baseRate = baseRates.rateAt(at);

		PricingResult priced = engine.price(
				new PricingInput(receivable.getFaceValue(), receivable.getType(), termMonths, paymentCurrency),
				baseRate,
				fxRate);

		receivable.setStatus(ReceivableStatus.SETTLED);
		receivables.saveAndFlush(receivable);

		SettlementEntity settlement = new SettlementEntity();
		settlement.setReceivableId(receivable.getId());
		settlement.setCedenteId(receivable.getCedenteId());
		settlement.setIdempotencyKey(idempotencyKey);
		settlement.setRequestHash(hash);
		settlement.setPaymentCurrency(paymentCurrency);
		settlement.setFaceValue(priced.faceValue());
		settlement.setTermMonths(priced.termMonths());
		settlement.setBaseRate(priced.baseRate());
		settlement.setSpread(priced.spread());
		settlement.setPresentValueBrl(priced.presentValueBrl());
		settlement.setDiscountBrl(priced.discountBrl());
		settlement.setFxRateId(fxRateId);
		settlement.setFxRate(priced.fxRate());
		settlement.setNetAmount(priced.netAmount());
		settlement.setSettledAt(OffsetDateTime.now(clock));
		return toSnapshot(settlements.saveAndFlush(settlement));
	}

	private static SettlementResult logged(String result, SettlementSnapshot snapshot) {
		logEvent(result, snapshot.id(), snapshot.receivableId(), snapshot.cedenteId());
		return new SettlementResult(snapshot, "replayed".equals(result));
	}

	private static void logEvent(String result, UUID settlementId, UUID receivableId, UUID cedenteId) {
		LoggingEventBuilder event = log.atInfo().addKeyValue("result", result);
		event = addId(event, "settlementId", settlementId);
		event = addId(event, "receivableId", receivableId);
		event = addId(event, "cedenteId", cedenteId);
		event.log("Liquidação");
	}

	private static LoggingEventBuilder addId(LoggingEventBuilder event, String key, UUID value) {
		if (value == null) {
			return event;
		}
		return event.addKeyValue(key, value.toString());
	}

	private static boolean sameHash(SettlementEntity settlement, String hash) {
		return settlement.getRequestHash().trim().equals(hash);
	}

	private static boolean causedBy(Throwable exception, String constraint) {
		Throwable current = exception;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && message.contains(constraint)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	static String requestHash(UUID receivableId, PaymentCurrency paymentCurrency) {
		byte[] canonical = (receivableId + "\n" + paymentCurrency.name()).getBytes(StandardCharsets.UTF_8);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 indisponível", exception);
		}
	}

	private static SettlementSnapshot toSnapshot(SettlementEntity settlement) {
		return new SettlementSnapshot(
				settlement.getId(),
				settlement.getReceivableId(),
				settlement.getCedenteId(),
				settlement.getIdempotencyKey(),
				settlement.getRequestHash(),
				settlement.getPaymentCurrency(),
				settlement.getFaceValue(),
				settlement.getTermMonths(),
				settlement.getBaseRate(),
				settlement.getSpread(),
				settlement.getPresentValueBrl(),
				settlement.getDiscountBrl(),
				settlement.getFxRateId(),
				settlement.getFxRate(),
				settlement.getNetAmount(),
				settlement.getSettledAt());
	}

}
