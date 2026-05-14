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
 * Redis configuration — bugun rate-limit bucket'lari icin kullanilir,
 * yarin cache / queue / oturum gibi ihtiyaclar icin de hazirdir.
 *
 * <p>Spring Boot'un {@code spring.data.redis.*} auto-config'i
 * {@link RedisConnectionFactory} ve {@code StringRedisTemplate} bean'lerini
 * zaten saglar. Buradaki ek bean'ler:
 * <ul>
 *   <li>{@link RedisTemplate}<{@code String, Object}> — JSON serializer ile;
 *       DTO/Map gibi yapilar Redis'e kolayca yazilabilsin diye.</li>
 *   <li>Raw Lettuce {@link RedisClient} ve {@link StatefulRedisConnection} —
 *       Bucket4j ProxyManager bunlari ister; Spring'in
 *       {@code LettuceConnectionFactory}'sinden alinamiyor.</li>
 *   <li>{@link LettuceBasedProxyManager} — rate-limit interceptor'unun ihtiyaci
 *       olan distributed Bucket4j proxy manager'i.</li>
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
     * JSON-serialized RedisTemplate — DTO/Map degerlerini Redis'te insanin
     * okuyabilecegi formatta saklamak icin. Cache/queue eklenirken bunu kullan.
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
     * Bucket4j Lettuce entegrasyonu Spring Data Redis'in soyutlamasi yerine
     * Lettuce'in kendi {@link RedisClient}'ina ihtiyac duyar. Ayri bir baglanti
     * kurulur — Spring'in connection factory'si ile karismaz.
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
     * Bucket4j key=String, value=byte[] codec'i ister. ProxyManager
     * tek bir paylasilan baglanti uzerinden tum istekleri sirayla pipelinelar.
     */
    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, byte[]> bucketRedisConnection(RedisClient client) {
        return client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    /**
     * Distributed token-bucket proxy manager. Her bucket Redis'te bir anahtar
     * olarak yasar; istek geldikce CAS (compare-and-swap) ile guncellenir.
     *
     * <p>Bucket TTL'i Bucket4j tarafindan kullanim hizina gore ayarlanir
     * (basedOnTimeForRefillingBucketUpToMax). Bos kalan bucket'lar 10 dakika
     * sonra Redis'ten dusurulur — bellek sismez.
     */
    @Bean
    public ProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
