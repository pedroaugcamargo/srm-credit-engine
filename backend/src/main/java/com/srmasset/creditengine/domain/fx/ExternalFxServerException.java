package com.srmasset.creditengine.domain.fx;

public class ExternalFxServerException extends RuntimeException {

	public ExternalFxServerException() {
		super("Provedor de câmbio indisponível.");
	}

}
