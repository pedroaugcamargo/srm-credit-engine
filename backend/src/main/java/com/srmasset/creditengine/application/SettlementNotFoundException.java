package com.srmasset.creditengine.application;

public class SettlementNotFoundException extends RuntimeException {

	public SettlementNotFoundException() {
		super("Liquidação não encontrada.");
	}

}
