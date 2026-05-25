package com.ticketsystem.it_service_backend.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis configuration — today it backs the rate-limit buckets, tomorrow it is
 * ready for cache, queue or session needs.
 *
 * <p>Spring Boot's {@code spring.data.redis.*} auto-configuration already
 * provides the {@link RedisConnectionFactory} and {@code StringRedisTemplate}
 * beans. The extra beans defined here are:
 * <ul>
 *   <li>{@link RedisTemplate}<{@code String, Object}> — wired with a JSON
 *       serializer so DTO/Map structures can be written to Redis easily.</li>
 *   <li>Raw Lettuce {@link RedisClient} and {@link StatefulRedisConnection} —
 *       required by the Bucket4j ProxyManager and not obtainable from Spring's
 *       {@code LettuceConnectionFactory}.</li>
 *   <li>{@link LettuceBasedProxyManager} — the distributed Bucket4j proxy
 *       manager consumed by the rate-limit interceptor.</li>
 * </ul>
 */
@Log4j2
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
     * JSON-serialized RedisTemplate — stores DTO/Map values in a human-readable
     * format in Redis. Reach for this when wiring up cache or queue code.
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
     * The Bucket4j Lettuce integration needs Lettuce's own {@link RedisClient}
     * rather than the Spring Data Redis abstraction. A separate connection is
     * established here so it does not interfere with Spring's connection factory.
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
        log.info("Bucket4j Redis client kuruluyor. host={}, port={}", host, port);
        return RedisClient.create(uriBuilder.build());
    }

    /**
     * Bucket4j requires a key=String, value=byte[] codec. The ProxyManager
     * pipelines every request over a single shared connection.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucketRedisConnection(RedisClient client) {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * Distributed token-bucket proxy manager. Each bucket lives as a key in
     * Redis and is updated via CAS (compare-and-swap) on every request.
     *
     * <p>The bucket TTL is sized by Bucket4j based on the refill rate
     * (basedOnTimeForRefillingBucketUpToMax). Idle buckets are evicted from
     * Redis after 10 minutes so memory does not balloon.
     */
    @Bean
    public ProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
