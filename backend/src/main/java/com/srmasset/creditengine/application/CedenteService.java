package com.srmasset.creditengine.application;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.srmasset.creditengine.persistence.CedenteRepository;

@Service
public class CedenteService {

	private final CedenteRepository cedentes;

	public CedenteService(CedenteRepository cedentes) {
		this.cedentes = cedentes;
	}

	@Transactional(readOnly = true)
	public List<CedenteSummary> list() {
		return cedentes.findAll(Sort.by("nome", "id")).stream()
				.map(cedente -> new CedenteSummary(cedente.getId(), cedente.getNome())).toList();
	}

}
