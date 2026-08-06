package com.project;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableDiscoveryClient
@Configurable
public class CreditCardsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditCardsApplication.class, args);
	}
	
	@Bean
	@LoadBalanced
	public RestTemplate restCardTemplate() {
		System.out.println("Creating & returning RestTemplate object");
		return new RestTemplate();
	}

}
