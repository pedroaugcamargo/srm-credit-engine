package com.srmasset.creditengine.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cedentes")
public class CedenteEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false)
	private String documento;

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

}
