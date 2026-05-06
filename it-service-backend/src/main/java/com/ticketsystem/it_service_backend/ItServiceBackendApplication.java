package com.ticketsystem.it_service_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ItServiceBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ItServiceBackendApplication.class, args);
	}

}
