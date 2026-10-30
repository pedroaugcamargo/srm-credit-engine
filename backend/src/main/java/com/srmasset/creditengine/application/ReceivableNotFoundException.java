package com.srmasset.creditengine.application;

public class ReceivableNotFoundException extends RuntimeException {

	public ReceivableNotFoundException() {
		super("Recebível não encontrado.");
	}

}
