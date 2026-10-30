package com.srmasset.creditengine.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fx_rates")
public class FxRateEntity {

	@Id
	@Generated(event = EventType.INSERT)
	private UUID id;

	@Column(name = "base_currency", nullable = false)
	private String baseCurrency;

	@Column(name = "quote_currency", nullable = false)
	private String quoteCurrency;

	@Column(nullable = false, precision = 18, scale = 8)
	private BigDecimal rate;

	@Column(name = "valid_from", nullable = false)
	private OffsetDateTime validFrom;

	@Column(nullable = false)
	private String source;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public UUID getId() {
		return id;
	}

	public String getBaseCurrency() {
		return baseCurrency;
	}

	public void setBaseCurrency(String baseCurrency) {
		this.baseCurrency = baseCurrency;
	}

	public String getQuoteCurrency() {
		return quoteCurrency;
	}

	public void setQuoteCurrency(String quoteCurrency) {
		this.quoteCurrency = quoteCurrency;
	}

	public BigDecimal getRate() {
		return rate;
	}

	public void setRate(BigDecimal rate) {
		this.rate = rate;
	}

	public OffsetDateTime getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(OffsetDateTime validFrom) {
		this.validFrom = validFrom;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

}
