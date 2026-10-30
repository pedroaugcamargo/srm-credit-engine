package com.srmasset.creditengine.application;

public class CedenteNotFoundException extends RuntimeException {

	public CedenteNotFoundException() {
		super("Cedente não encontrado.");
	}

}
