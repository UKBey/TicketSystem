package com.ticketsystem.llmservice.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
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
 * Redis configuration — used today for rate-limit buckets, ready tomorrow for
 * cache / queue use cases. Same pattern as the backend.
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
     * General-purpose {@link RedisTemplate} bean with JSON serialization for
     * cache / value storage.
     *
     * @param connectionFactory Spring Data Redis connection factory
     * @return string-key + JSON-value template
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
     * Low-level Lettuce {@link RedisClient} bean used by the Bucket4j
     * ProxyManager. Kept separate from Spring Data Redis because Bucket4j
     * requires a {@code byte[]} value codec.
     *
     * @return configured Lettuce client
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
     * Long-lived Lettuce connection for Bucket4j with a string-key +
     * {@code byte[]}-value codec.
     *
     * @param client the {@link #bucketRedisClient()} bean
     * @return shared Redis connection
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucketRedisConnection(RedisClient client) {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * Bucket4j {@link ProxyManager} bean (built via {@link Bucket4jLettuce}) —
     * manages buckets in a distributed fashion in Redis. Entries expire
     * 10 minutes after the last write via TTL.
     *
     * @param connection the {@link #bucketRedisConnection(RedisClient)} bean
     * @return proxy manager used by the rate-limit interceptor
     */
    @Bean
    public ProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
