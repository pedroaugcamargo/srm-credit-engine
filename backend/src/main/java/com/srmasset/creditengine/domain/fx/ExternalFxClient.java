package com.srmasset.creditengine.domain.fx;

public interface ExternalFxClient {

	ExternalFxQuote fetch(String base, String quote);

}
