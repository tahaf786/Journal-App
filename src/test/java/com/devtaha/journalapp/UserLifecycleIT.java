package com.devtaha.journalapp;

import com.devtaha.journalapp.entry.JournalEntryRepository;
import com.devtaha.journalapp.event.ActivityStatsRepository;
import com.devtaha.journalapp.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    UserRepository users;

    @Autowired
    JournalEntryRepository entries;

    @Autowired
    ActivityStatsRepository stats;

    @Test
    void profileCanBeUpdated() throws Exception {
        String token = newUserToken();

        putAs("/api/v1/users/me", token, Map.of("displayName", "New Name")).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("New Name"));
        assertThat(getJson("/api/v1/users/me", token).get("displayName").asText()).isEqualTo("New Name");
    }

    @Test
    void passwordChangeNeedsTheCurrentPassword() throws Exception {
        String username = uniqueName("pw");
        register(username);
        String token = login(username, PASSWORD).get("accessToken").asText();

        putAs("/api/v1/users/me", token, Map.of("displayName", "Pw", "currentPassword", "wrong-current",
                "newPassword", "a-brand-new-password")).andExpect(status().isBadRequest());

        putAs("/api/v1/users/me", token, Map.of("displayName", "Pw", "currentPassword", PASSWORD,
                "newPassword", "a-brand-new-password")).andExpect(status().isOk());
        assertThat(login(username, "a-brand-new-password").get("accessToken").asText()).isNotBlank();
        perform(post("/api/v1/auth/login"), null, Map.of("username", username, "password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletingTheUserRemovesEntriesAndStatsTogether() throws Exception {
        String username = uniqueName("leaver");
        register(username);
        String token = login(username, PASSWORD).get("accessToken").asText();
        JsonNode profile = getJson("/api/v1/users/me", token);
        String userId = profile.get("id").asText();
        String entryId = createEntry(token, "One", "1").get("id").asText();
        createEntry(token, "Two", "2");
        getJson("/api/v1/entries/" + entryId, token);
        await().atMost(Duration.ofSeconds(30)).until(() -> stats.existsById(userId));

        deleteAs("/api/v1/users/me", token).andExpect(status().isNoContent());

        assertThat(users.existsById(userId)).isFalse();
        assertThat(entries.findByUserId(userId, Pageable.unpaged())).isEmpty();
        assertThat(stats.existsById(userId)).isFalse();
        getAs("/api/v1/entries/" + entryId, token).andExpect(status().isNotFound());
        perform(post("/api/v1/auth/login"), null, Map.of("username", username, "password", PASSWORD))
                .andExpect(status().isUnauthorized());
    }
}
