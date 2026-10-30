package com.srmasset.creditengine.domain.fx;

public class FxRateUnavailableException extends RuntimeException {

	public FxRateUnavailableException() {
		super("Nenhuma taxa de câmbio vigente.");
	}

}
