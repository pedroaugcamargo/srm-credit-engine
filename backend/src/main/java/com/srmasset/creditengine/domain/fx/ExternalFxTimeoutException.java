package com.srmasset.creditengine.domain.fx;

public class ExternalFxTimeoutException extends RuntimeException {

	public ExternalFxTimeoutException(Throwable cause) {
		super("Provedor de câmbio não respondeu a tempo.", cause);
	}

	public ExternalFxTimeoutException() {
		super("Provedor de câmbio não respondeu a tempo.");
	}

}
