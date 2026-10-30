package com.srmasset.creditengine.web;

import java.util.UUID;

import com.srmasset.creditengine.application.CedenteSummary;

// Sem o documento: o operador escolhe o cedente pelo nome.
public record CedenteResponse(UUID id, String nome) {

	static CedenteResponse from(CedenteSummary cedente) {
		return new CedenteResponse(cedente.id(), cedente.nome());
	}

}
