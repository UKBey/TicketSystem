package com.ticketsystem.it_service_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration binding for the {@code app.alerts.*} section of
 * {@code application.yml}.
 *
 * <p>Drives the "waiting too long" dashboard alerts: how many hours a ticket
 * may sit in {@code WAITING_FOR_CUSTOMER} (waiting on the customer to reply) or
 * in {@code RESOLVED} (waiting on the customer to confirm/close) before it is
 * surfaced as an alert. Both are measured from when the ticket <em>entered</em>
 * that state, not from creation. Overridable via env vars
 * ({@code ALERTS_WAITING_FOR_CUSTOMER_MAX_HOURS} / {@code ALERTS_RESOLVED_MAX_HOURS}).
 */
@Component
@ConfigurationProperties(prefix = "app.alerts")
@Getter
@Setter
public class AlertProperties {

    /** Max hours a ticket may stay in WAITING_FOR_CUSTOMER before alerting. */
    private int waitingForCustomerMaxHours = 48;

    /** Max hours a ticket may stay in RESOLVED (awaiting closure) before alerting. */
    private int resolvedMaxHours = 72;
}
