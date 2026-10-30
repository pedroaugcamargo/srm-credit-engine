package com.srmasset.creditengine;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.srmasset.creditengine.domain.pricing.RoundingPolicy;

@SpringBootApplication
@EnableConfigurationProperties(RoundingPolicy.class)
public class CreditEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditEngineApplication.class, args);

		System.out.println("Credit Engine Application started");
	}

	@Bean
	Clock operationClock() {
		return Clock.system(ZoneId.of("America/Sao_Paulo"));
	}

}
