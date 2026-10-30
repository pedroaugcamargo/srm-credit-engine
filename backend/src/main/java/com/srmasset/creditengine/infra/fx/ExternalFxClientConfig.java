package com.srmasset.creditengine.infra.fx;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.srmasset.creditengine.domain.fx.ExternalFxClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;

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
	CircuitBreakerRegistry externalFxCircuitBreakers() {
		return CircuitBreakerRegistry.ofDefaults();
	}

	@Bean
	ExternalFxClient externalFxClient(
			ExternalFxProviderProperties properties,
			ScheduledExecutorService externalFxScheduler,
			CircuitBreakerRegistry externalFxCircuitBreakers,
			MeterRegistry meterRegistry) {
		TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(externalFxCircuitBreakers).bindTo(meterRegistry);
		return ResilientExternalFxClient.create(
				new ConfigurableExternalFxClient(properties),
				properties,
				externalFxScheduler,
				externalFxCircuitBreakers,
				meterRegistry);
	}

}
