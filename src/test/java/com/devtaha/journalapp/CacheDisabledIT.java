package com.devtaha.journalapp;

import com.devtaha.journalapp.entry.JournalEntry;
import com.devtaha.journalapp.entry.JournalEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.cache.enabled=false")
class CacheDisabledIT extends AbstractIntegrationTest {

    @Autowired
    CacheManager cacheManager;

    @Autowired
    JournalEntryRepository entries;

    @Test
    void cachingCanBeSwitchedOff() throws Exception {
        assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);

        String token = newUserToken();
        String id = createEntry(token, "Fresh", "original").get("id").asText();
        getJson("/api/v1/entries/" + id, token);

        JournalEntry stored = entries.findById(id).orElseThrow();
        stored.setTitle("Changed directly in MongoDB");
        entries.save(stored);

        assertThat(getJson("/api/v1/entries/" + id, token).get("title").asText())
                .isEqualTo("Changed directly in MongoDB");
    }
}
