package com.srmasset.creditengine.application;

public class IdempotencyKeyReusedException extends RuntimeException {

	public IdempotencyKeyReusedException() {
		super("Idempotency-Key já utilizada com outro conteúdo.");
	}

}
