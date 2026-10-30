package com.srmasset.creditengine.domain.fx;

public class ExternalFxClientException extends RuntimeException {

	public ExternalFxClientException() {
		super("Provedor de câmbio recusou a consulta.");
	}

}
