package com.srmasset.creditengine.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import com.srmasset.creditengine.domain.pricing.PaymentCurrency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "settlements")
public class SettlementEntity {

	@Id
	@Generated(event = EventType.INSERT)
	private UUID id;

	@Column(name = "receivable_id", nullable = false)
	private UUID receivableId;

	@Column(name = "cedente_id", nullable = false)
	private UUID cedenteId;

	@Column(name = "idempotency_key", nullable = false)
	private UUID idempotencyKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_currency", nullable = false)
	private PaymentCurrency paymentCurrency;

	@Column(name = "face_value", nullable = false, precision = 19, scale = 2)
	private BigDecimal faceValue;

	@Column(name = "term_months", nullable = false, precision = 12, scale = 6)
	private BigDecimal termMonths;

	@Column(name = "base_rate", nullable = false, precision = 12, scale = 8)
	private BigDecimal baseRate;

	@Column(nullable = false, precision = 12, scale = 8)
	private BigDecimal spread;

	@Column(name = "present_value_brl", nullable = false, precision = 19, scale = 2)
	private BigDecimal presentValueBrl;

	@Column(name = "discount_brl", nullable = false, precision = 19, scale = 2)
	private BigDecimal discountBrl;

	@Column(name = "fx_rate_id")
	private UUID fxRateId;

	@Column(name = "fx_rate", precision = 18, scale = 8)
	private BigDecimal fxRate;

	@Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal netAmount;

	@Column(name = "settled_at", nullable = false)
	private OffsetDateTime settledAt;

	public UUID getId() {
		return id;
	}

	public UUID getReceivableId() {
		return receivableId;
	}

	public void setReceivableId(UUID receivableId) {
		this.receivableId = receivableId;
	}

	public UUID getCedenteId() {
		return cedenteId;
	}

	public void setCedenteId(UUID cedenteId) {
		this.cedenteId = cedenteId;
	}

	public UUID getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(UUID idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public void setRequestHash(String requestHash) {
		this.requestHash = requestHash;
	}

	public PaymentCurrency getPaymentCurrency() {
		return paymentCurrency;
	}

	public void setPaymentCurrency(PaymentCurrency paymentCurrency) {
		this.paymentCurrency = paymentCurrency;
	}

	public BigDecimal getFaceValue() {
		return faceValue;
	}

	public void setFaceValue(BigDecimal faceValue) {
		this.faceValue = faceValue;
	}

	public BigDecimal getTermMonths() {
		return termMonths;
	}

	public void setTermMonths(BigDecimal termMonths) {
		this.termMonths = termMonths;
	}

	public BigDecimal getBaseRate() {
		return baseRate;
	}

	public void setBaseRate(BigDecimal baseRate) {
		this.baseRate = baseRate;
	}

	public BigDecimal getSpread() {
		return spread;
	}

	public void setSpread(BigDecimal spread) {
		this.spread = spread;
	}

	public BigDecimal getPresentValueBrl() {
		return presentValueBrl;
	}

	public void setPresentValueBrl(BigDecimal presentValueBrl) {
		this.presentValueBrl = presentValueBrl;
	}

	public BigDecimal getDiscountBrl() {
		return discountBrl;
	}

	public void setDiscountBrl(BigDecimal discountBrl) {
		this.discountBrl = discountBrl;
	}

	public UUID getFxRateId() {
		return fxRateId;
	}

	public void setFxRateId(UUID fxRateId) {
		this.fxRateId = fxRateId;
	}

	public BigDecimal getFxRate() {
		return fxRate;
	}

	public void setFxRate(BigDecimal fxRate) {
		this.fxRate = fxRate;
	}

	public BigDecimal getNetAmount() {
		return netAmount;
	}

	public void setNetAmount(BigDecimal netAmount) {
		this.netAmount = netAmount;
	}

	public OffsetDateTime getSettledAt() {
		return settledAt;
	}

	public void setSettledAt(OffsetDateTime settledAt) {
		this.settledAt = settledAt;
	}

}
