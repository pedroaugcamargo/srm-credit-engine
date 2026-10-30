package com.srmasset.creditengine.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "base_rates")
public class BaseRateEntity {

	@Id
	private UUID id;

	@Column(nullable = false, precision = 12, scale = 8)
	private BigDecimal rate;

	@Column(name = "valid_from", nullable = false)
	private OffsetDateTime validFrom;

	public BigDecimal getRate() {
		return rate;
	}

	public OffsetDateTime getValidFrom() {
		return validFrom;
	}

}
