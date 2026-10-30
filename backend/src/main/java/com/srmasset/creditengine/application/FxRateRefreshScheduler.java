package com.srmasset.creditengine.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import com.srmasset.creditengine.infra.fx.ExternalFxProviderProperties;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "credit-engine.fx.provider.refresh-enabled", havingValue = "true")
public class FxRateRefreshScheduler implements SchedulingConfigurer {

	private final FxRateRefreshService refresh;
	private final ExternalFxProviderProperties properties;

	public FxRateRefreshScheduler(FxRateRefreshService refresh, ExternalFxProviderProperties properties) {
		this.refresh = refresh;
		this.properties = properties;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		registrar.addFixedDelayTask(new IntervalTask(
				refresh::refresh, properties.refreshInterval(), properties.refreshInterval()));
	}

}
