package com.ticketsystem.llmservice.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration — bugun rate-limit bucket'lari icin kullanilir, yarin
 * cache / queue gibi ihtiyaclar icin de hazirdir. Backend ile ayni desen.
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.timeout:5s}")
    private Duration timeout;

    /**
     * Genel amaclı cache / değer depolama için JSON serileştirmeli
     * {@link RedisTemplate} bean'i.
     *
     * @param connectionFactory Spring Data Redis bağlantı fabrikası
     * @return string anahtar + JSON değer template'i
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keys = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer values = new GenericJackson2JsonRedisSerializer();
        template.setKeySerializer(keys);
        template.setHashKeySerializer(keys);
        template.setValueSerializer(values);
        template.setHashValueSerializer(values);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Bucket4j ProxyManager'in kullandığı düşük seviyeli Lettuce
     * {@link RedisClient} bean'i. Spring Data Redis'ten ayrı tutulur çünkü
     * Bucket4j {@code byte[]} value codec ister.
     *
     * @return yapılandırılmış Lettuce istemcisi
     */
    @Bean(destroyMethod = "shutdown")
    public RedisClient bucketRedisClient() {
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(timeout);
        if (password != null && !password.isBlank()) {
            uriBuilder.withPassword(password.toCharArray());
        }
        log.info("LLM Bucket4j Redis client kuruluyor. host={}, port={}", host, port);
        return RedisClient.create(uriBuilder.build());
    }

    /**
     * Bucket4j için string anahtar + {@code byte[]} value codec'li
     * uzun ömürlü Lettuce bağlantısı.
     *
     * @param client {@link #bucketRedisClient()} bean'i
     * @return paylaşımlı Redis bağlantısı
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucketRedisConnection(RedisClient client) {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * Bucket4j {@link LettuceBasedProxyManager} bean'i — kovaların Redis'te
     * dağıtık olarak yönetilmesini sağlar. Son yazımdan 10 dk sonra TTL ile
     * temizlenir.
     *
     * @param connection {@link #bucketRedisConnection(RedisClient)} bean'i
     * @return rate-limit interceptor'in kullanacağı proxy manager
     */
    @Bean
    public ProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
