package com.srmasset.creditengine.application;

public class IdempotencyKeyRequiredException extends RuntimeException {

	public IdempotencyKeyRequiredException() {
		super("Informe um Idempotency-Key válido.");
	}

}
