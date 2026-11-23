package com.devtaha.journalapp.config;

import com.devtaha.journalapp.entry.EntryResponse;
import com.devtaha.journalapp.entry.PageResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Redis-backed caches for the read paths. Setting app.cache.enabled=false
 * swaps in a no-op cache manager so the same code runs without caching.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String ENTRIES = "entries";
    public static final String ENTRY_PAGES = "entryPages";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private final ObjectMapper cacheMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Bean
    @ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
    CacheManager cacheManager(RedisConnectionFactory connectionFactory, AppProperties props) {
        JavaType pageType = cacheMapper.getTypeFactory()
                .constructParametricType(PageResponse.class, EntryResponse.class);
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("journal:")
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                ENTRIES, base.entryTtl(props.cache().entryTtl()).serializeValuesWith(SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(cacheMapper, EntryResponse.class))),
                ENTRY_PAGES, base.entryTtl(props.cache().pageTtl()).serializeValuesWith(SerializationPair
                        .fromSerializer(new Jackson2JsonRedisSerializer<>(cacheMapper, pageType))));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(1)))
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.enabled", havingValue = "false")
    CacheManager noOpCacheManager() {
        return new NoOpCacheManager();
    }

    /** A Redis outage degrades to uncached reads instead of failing requests. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed for {}: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed for {}: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed for {}: {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed for {}: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
