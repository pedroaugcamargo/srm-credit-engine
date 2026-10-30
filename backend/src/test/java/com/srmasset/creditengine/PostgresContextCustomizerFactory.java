package com.srmasset.creditengine;

import java.util.List;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgresContextCustomizerFactory implements ContextCustomizerFactory {

	@Override
	public ContextCustomizer createContextCustomizer(Class<?> testClass,
			List<ContextConfigurationAttributes> configAttributes) {
		return new PostgresContextCustomizer();
	}

	private static final class PostgresContextCustomizer implements ContextCustomizer {

		private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
				DockerImageName.parse("postgres:18-alpine"));

		static {
			POSTGRES.setCommand("postgres", "-c", "max_connections=300");
			POSTGRES.start();
		}

		@Override
		public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
			TestPropertyValues.of("spring.datasource.url=" + POSTGRES.getJdbcUrl(),
					"spring.datasource.username=" + POSTGRES.getUsername(),
					"spring.datasource.password=" + POSTGRES.getPassword()).applyTo(context.getEnvironment());
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof PostgresContextCustomizer;
		}

		@Override
		public int hashCode() {
			return PostgresContextCustomizer.class.hashCode();
		}

	}

}
