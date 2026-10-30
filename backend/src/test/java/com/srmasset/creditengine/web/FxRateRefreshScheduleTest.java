package com.srmasset.creditengine.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.srmasset.creditengine.application.FxRateRefreshScheduler;

@SpringBootTest(properties = "credit-engine.fx.provider.refresh-enabled=true")
class FxRateRefreshScheduleTest {

	@Autowired
	private ObjectProvider<FxRateRefreshScheduler> scheduler;

	@Test
	void enablesTheSchedulerWhenConfigured() {
		assertNotNull(scheduler.getIfAvailable());
	}

}
