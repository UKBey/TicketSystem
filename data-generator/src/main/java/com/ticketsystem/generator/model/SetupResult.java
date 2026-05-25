package com.ticketsystem.generator.model;

import java.util.List;
import java.util.Map;

/**
 * Output of SetupGenerator — passed on to TicketGenerator.
 *
 * @param agentAdmin            agent_admin session (sourced from config)
 * @param agents                agent sessions logged in during setup
 * @param customers             customer sessions logged in during setup
 * @param productByName         "product name" → product ID
 * @param topicByProductAndName "product name::topic name" → topic ID
 */
public record SetupResult(
        UserSession adminAgent,
        List<UserSession> agents,
        List<UserSession> customers,
        Map<String, Long> productByName,
        Map<String, Long> topicByProductAndName
) {

    /**
     * Builds the key for the {@link #topicByProductAndName()} map.
     *
     * @param productName product name
     * @param topicName   topic name
     * @return a composite key in the form {@code "productName::topicName"}
     */
    public static String topicKey(String productName, String topicName) {
        return productName + "::" + topicName;
    }
}
