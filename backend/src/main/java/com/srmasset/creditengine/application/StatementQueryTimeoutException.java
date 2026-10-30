package com.srmasset.creditengine.application;

public class StatementQueryTimeoutException extends RuntimeException {

	public StatementQueryTimeoutException(Throwable cause) {
		super("A consulta do extrato excedeu o tempo limite.", cause);
	}

}
