package com.srmasset.creditengine.infra.fx;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.srmasset.creditengine.domain.fx.ExternalFxClient;

@Configuration
class ExternalFxClientConfig {

	@Bean(destroyMethod = "shutdown")
	ScheduledExecutorService externalFxScheduler() {
		return Executors.newScheduledThreadPool(4, runnable -> {
			Thread thread = new Thread(runnable, "external-fx");
			thread.setDaemon(true);
			return thread;
		});
	}

	@Bean
	ExternalFxClient externalFxClient(ExternalFxProviderProperties properties, ScheduledExecutorService externalFxScheduler) {
		return ResilientExternalFxClient.create(
				new ConfigurableExternalFxClient(properties),
				properties,
				externalFxScheduler);
	}

}
