package com.srmasset.creditengine.domain.fx;

public class FxRateStaleException extends RuntimeException {

	public FxRateStaleException() {
		super("Taxa de câmbio desatualizada. Atualize antes de liquidar.");
	}

}
