package com.srmasset.creditengine.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.srmasset.creditengine.application.CedenteService;

@RestController
@RequestMapping("/api/v1/cedentes")
public class CedenteController {

	private final CedenteService cedentes;

	public CedenteController(CedenteService cedentes) {
		this.cedentes = cedentes;
	}

	@GetMapping
	public List<CedenteResponse> list() {
		return cedentes.list().stream().map(CedenteResponse::from).toList();
	}

}
