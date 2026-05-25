package com.ticketsystem.generator.model;

import java.util.List;
import java.util.Map;

/**
 * SetupGenerator çıktısı — TicketGenerator'a aktarılır.
 *
 * @param agentAdmin            agent_admin oturumu (config'ten gelir)
 * @param agents                Kurulum sırasında giriş yapılmış agent oturumları
 * @param customers             Kurulum sırasında giriş yapılmış customer oturumları
 * @param productByName         "Ürün adı" → product ID
 * @param topicByProductAndName "Ürün adı::Topic adı" → topic ID
 */
public record SetupResult(
        UserSession adminAgent,
        List<UserSession> agents,
        List<UserSession> customers,
        Map<String, Long> productByName,
        Map<String, Long> topicByProductAndName
) {

    /**
     * {@link #topicByProductAndName()} map'inin anahtarını üretir.
     *
     * @param productName ürün adı
     * @param topicName   topic adı
     * @return {@code "productName::topicName"} formatında composite anahtar
     */
    public static String topicKey(String productName, String topicName) {
        return productName + "::" + topicName;
    }
}
