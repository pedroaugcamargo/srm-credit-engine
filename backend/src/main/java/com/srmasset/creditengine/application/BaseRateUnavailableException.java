package com.srmasset.creditengine.application;

public class BaseRateUnavailableException extends RuntimeException {

	public BaseRateUnavailableException() {
		super("Nenhuma taxa base vigente.");
	}

}
