package com.srmasset.creditengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.srmasset.creditengine.domain.pricing.RoundingPolicy;

@SpringBootApplication
@EnableConfigurationProperties(RoundingPolicy.class)
public class CreditEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditEngineApplication.class, args);

		System.out.println("Credit Engine Application started");
	}

}
