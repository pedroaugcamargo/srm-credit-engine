package com.srmasset.creditengine.application;

public class ReceivableAlreadySettledException extends RuntimeException {

	public ReceivableAlreadySettledException() {
		super("Recebível já liquidado.");
	}

}
