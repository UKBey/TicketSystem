package com.ticketsystem.it_service_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the IT Service Desk backend.
 *
 * <p>Boots the Spring application context, scans the
 * {@code com.ticketsystem.it_service_backend} package and starts the embedded
 * web server on port {@code 8081}. The annotations enable cross-cutting
 * features used throughout the codebase:
 * <ul>
 *   <li>{@link EnableCaching} — activates {@code @Cacheable}/{@code @CacheEvict}
 *       backed by the Caffeine manager in
 *       {@link com.ticketsystem.it_service_backend.config.CacheConfig}.</li>
 *   <li>{@link EnableAsync} — lets {@code @Async} methods (notifications, email
 *       dispatch) run on a separate executor.</li>
 *   <li>{@link EnableScheduling} — enables cron-driven jobs such as the SLA
 *       breach scanner in
 *       {@link com.ticketsystem.it_service_backend.scheduler.SlaNotificationScheduler}.</li>
 * </ul>
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class ItServiceBackendApplication {

	/**
	 * Standard Spring Boot launcher.
	 *
	 * @param args command-line arguments forwarded to {@link SpringApplication#run}
	 */
	public static void main(String[] args) {
		SpringApplication.run(ItServiceBackendApplication.class, args);
	}

}
