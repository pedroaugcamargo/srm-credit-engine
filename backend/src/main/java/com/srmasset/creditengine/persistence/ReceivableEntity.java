package com.srmasset.creditengine.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.srmasset.creditengine.domain.pricing.ReceivableType;
import com.srmasset.creditengine.domain.receivable.ReceivableStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "receivables")
public class ReceivableEntity {

	@Id
	@Generated(event = EventType.INSERT)
	private UUID id;

	@Column(name = "cedente_id", nullable = false)
	private UUID cedenteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReceivableType type;

	@Column(name = "face_value", nullable = false, precision = 19, scale = 2)
	private BigDecimal faceValue;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReceivableStatus status;

	@Version
	@Column(nullable = false)
	private Long version;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	public UUID getId() {
		return id;
	}

	public UUID getCedenteId() {
		return cedenteId;
	}

	public void setCedenteId(UUID cedenteId) {
		this.cedenteId = cedenteId;
	}

	public ReceivableType getType() {
		return type;
	}

	public void setType(ReceivableType type) {
		this.type = type;
	}

	public BigDecimal getFaceValue() {
		return faceValue;
	}

	public void setFaceValue(BigDecimal faceValue) {
		this.faceValue = faceValue;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDate dueDate) {
		this.dueDate = dueDate;
	}

	public ReceivableStatus getStatus() {
		return status;
	}

	public void setStatus(ReceivableStatus status) {
		this.status = status;
	}

	public Long getVersion() {
		return version;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(OffsetDateTime createdAt) {
		this.createdAt = createdAt;
	}

}
