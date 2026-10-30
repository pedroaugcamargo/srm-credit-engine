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
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	private final ReceivableRepository receivables;
	private final SettlementRepository settlements;
	private final DbBaseRateProvider baseRates;
	private final DbFxRateProvider fxRates;
	private final PricingEngine engine;
	private final TermCalculator termCalculator;
	private final Clock clock;

	public SettlementService(
			ReceivableRepository receivables,
			SettlementRepository settlements,
			DbBaseRateProvider baseRates,
			DbFxRateProvider fxRates,
			PricingEngine engine,
			TermCalculator termCalculator,
			Clock clock) {
		this.receivables = receivables;
		this.settlements = settlements;
		this.baseRates = baseRates;
		this.fxRates = fxRates;
		this.engine = engine;
		this.termCalculator = termCalculator;
		this.clock = clock;
	}

	@Transactional
	public SettlementSnapshot settle(UUID idempotencyKey, UUID receivableId, PaymentCurrency paymentCurrency) {
		ReceivableEntity receivable = receivables.findById(receivableId).orElseThrow(ReceivableNotFoundException::new);
		if (receivable.getStatus() == ReceivableStatus.SETTLED) {
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
		settlement.setRequestHash(requestHash(receivableId, paymentCurrency));
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
