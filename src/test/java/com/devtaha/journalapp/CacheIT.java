package com.devtaha.journalapp;

import com.devtaha.journalapp.config.CacheConfig;
import com.devtaha.journalapp.entry.JournalEntry;
import com.devtaha.journalapp.entry.JournalEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CacheIT extends AbstractIntegrationTest {

    @Autowired
    JournalEntryRepository entries;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    StringRedisTemplate redis;

    private String userIdOf(String token) throws Exception {
        return getJson("/api/v1/users/me", token).get("id").asText();
    }

    @Test
    void secondReadIsServedFromRedis() throws Exception {
        String token = newUserToken();
        String userId = userIdOf(token);
        String id = createEntry(token, "Cached", "original").get("id").asText();
        String key = userId + ":" + id;
        assertThat(cacheManager.getCache(CacheConfig.ENTRIES).get(key)).isNull();

        getJson("/api/v1/entries/" + id, token);
        assertThat(cacheManager.getCache(CacheConfig.ENTRIES).get(key)).isNotNull();

        // Change the document behind the cache's back. A cache hit still returns the old value.
        JournalEntry stored = entries.findById(id).orElseThrow();
        stored.setTitle("Changed directly in MongoDB");
        entries.save(stored);

        assertThat(getJson("/api/v1/entries/" + id, token).get("title").asText()).isEqualTo("Cached");
    }

    @Test
    void updateEvictsTheCachedEntry() throws Exception {
        String token = newUserToken();
        String userId = userIdOf(token);
        String id = createEntry(token, "Before", "text").get("id").asText();
        String key = userId + ":" + id;
        getJson("/api/v1/entries/" + id, token);
        assertThat(cacheManager.getCache(CacheConfig.ENTRIES).get(key)).isNotNull();

        putAs("/api/v1/entries/" + id, token, Map.of("title", "After", "content", "text"))
                .andExpect(status().isOk());

        assertThat(cacheManager.getCache(CacheConfig.ENTRIES).get(key)).isNull();
        assertThat(getJson("/api/v1/entries/" + id, token).get("title").asText()).isEqualTo("After");
    }

    @Test
    void deleteEvictsTheCachedEntry() throws Exception {
        String token = newUserToken();
        String userId = userIdOf(token);
        String id = createEntry(token, "Doomed", "text").get("id").asText();
        getJson("/api/v1/entries/" + id, token);

        deleteAs("/api/v1/entries/" + id, token).andExpect(status().isNoContent());

        assertThat(cacheManager.getCache(CacheConfig.ENTRIES).get(userId + ":" + id)).isNull();
        getAs("/api/v1/entries/" + id, token).andExpect(status().isNotFound());
    }

    @Test
    void createEvictsCachedPages() throws Exception {
        String token = newUserToken();
        createEntry(token, "One", "1");
        JsonNode before = getJson("/api/v1/entries", token);
        assertThat(before.get("totalElements").asLong()).isEqualTo(1);

        createEntry(token, "Two", "2");

        assertThat(getJson("/api/v1/entries", token).get("totalElements").asLong()).isEqualTo(2);
    }

    @Test
    void pageIsCachedBetweenWrites() throws Exception {
        String token = newUserToken();
        String userId = userIdOf(token);
        String id = createEntry(token, "Listed", "x").get("id").asText();
        getJson("/api/v1/entries", token);

        entries.deleteById(id);

        assertThat(getJson("/api/v1/entries", token).get("totalElements").asLong()).isEqualTo(1);
        assertThat(redis.keys("journal:" + CacheConfig.ENTRY_PAGES + "::" + userId + ":*")).isNotEmpty();
    }

    @Test
    void cachedValuesCarryAnExpiry() throws Exception {
        String token = newUserToken();
        String userId = userIdOf(token);
        String id = createEntry(token, "Expiring", "x").get("id").asText();
        getJson("/api/v1/entries/" + id, token);

        Long ttl = redis.getExpire("journal:" + CacheConfig.ENTRIES + "::" + userId + ":" + id);

        assertThat(ttl).isPositive().isLessThanOrEqualTo(600);
    }
}
