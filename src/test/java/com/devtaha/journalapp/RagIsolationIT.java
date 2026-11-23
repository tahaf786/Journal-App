package com.devtaha.journalapp;

import com.devtaha.journalapp.rag.ChatClient;
import com.devtaha.journalapp.rag.EmbeddingClient;
import com.devtaha.journalapp.rag.RagUnavailableException;
import com.devtaha.journalapp.user.Role;
import com.devtaha.journalapp.user.User;
import com.devtaha.journalapp.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Qdrant and real Kafka indexing, with a deterministic fake embedding model and a stub chat model, so the
 * only thing under test is the retrieval path: who can find what.
 */
@TestPropertySource(properties = "app.rag.enabled=true")
class RagIsolationIT extends AbstractIntegrationTest {

    static final GenericContainer<?> QDRANT = new GenericContainer<>("qdrant/qdrant:v1.12.4")
            .withExposedPorts(6333)
            .waitingFor(Wait.forHttp("/readyz").forPort(6333).withStartupTimeout(Duration.ofSeconds(60)));

    static {
        QDRANT.start();
    }

    @DynamicPropertySource
    static void qdrantProperties(DynamicPropertyRegistry registry) {
        registry.add("app.rag.qdrant-url", () -> "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
    }

    static final AtomicBoolean CHAT_DOWN = new AtomicBoolean(false);
    static final AtomicReference<String> LAST_PROMPT = new AtomicReference<>();

    @TestConfiguration
    static class Fakes {

        /** Bag of words hashed into 64 dimensions and normalised: same words, same direction. */
        @Bean
        @Primary
        EmbeddingClient fakeEmbeddingClient() {
            return text -> {
                float[] v = new float[64];
                for (String word : text.toLowerCase().split("[^a-z0-9]+")) {
                    if (!word.isBlank()) {
                        v[Math.floorMod(word.hashCode(), 64)] += 1f;
                    }
                }
                double norm = 0;
                for (float x : v) {
                    norm += x * x;
                }
                norm = Math.sqrt(Math.max(norm, 1e-9));
                for (int i = 0; i < v.length; i++) {
                    v[i] /= (float) norm;
                }
                return v;
            };
        }

        @Bean
        @Primary
        ChatClient fakeChatClient() {
            return (system, user) -> {
                if (CHAT_DOWN.get()) {
                    throw new RagUnavailableException("chat model down");
                }
                LAST_PROMPT.set(user);
                return "stub answer";
            };
        }
    }

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder encoder;

    private JsonNode ask(String token, String question) throws Exception {
        String body = perform(post("/api/v1/ask"), token, Map.of("question", question, "topK", 10))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private List<String> sourceIds(JsonNode response) {
        return response.get("sources").findValuesAsText("entryId");
    }

    private void awaitIndexed(String token, String question, String entryId) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(sourceIds(ask(token, question))).contains(entryId));
    }

    @Test
    void userANeverRetrievesUserBsEntriesAndTheOtherWayRound() throws Exception {
        String tokenA = newUserToken();
        String tokenB = newUserToken();
        String idA = createEntry(tokenA, "Zebra migration", "Zebra herds cross the river in autumn").get("id").asText();
        String idB = createEntry(tokenB, "Zebra migration plan",
                "Zebra herds cross the river in autumn, my secret itinerary").get("id").asText();

        awaitIndexed(tokenA, "zebra herds river autumn", idA);
        awaitIndexed(tokenB, "zebra herds river autumn", idB);

        JsonNode forA = ask(tokenA, "zebra herds river autumn migration itinerary");
        String promptForA = LAST_PROMPT.get();
        JsonNode forB = ask(tokenB, "zebra herds river autumn migration itinerary");
        assertThat(sourceIds(forA)).containsExactly(idA);
        assertThat(sourceIds(forB)).containsExactly(idB);
        assertThat(promptForA).doesNotContain("secret itinerary");
        assertThat(LAST_PROMPT.get()).contains("secret itinerary");

        // A user with no entries gets nothing, although other users' points are an exact match.
        JsonNode forNobody = ask(newUserToken(), "zebra herds river autumn migration itinerary");
        assertThat(forNobody.get("sources")).isEmpty();
        assertThat(forNobody.get("answer").asText()).contains("could not find");
    }

    @Test
    void deletingAnEntryRemovesItsPointFromSearch() throws Exception {
        String token = newUserToken();
        String keep = createEntry(token, "Quartz garden", "Quartz crystals in the garden shed").get("id").asText();
        String drop = createEntry(token, "Quartz sale", "Sold quartz crystals at the market").get("id").asText();
        awaitIndexed(token, "quartz crystals", keep);
        awaitIndexed(token, "quartz crystals", drop);

        deleteAs("/api/v1/entries/" + drop, token).andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertThat(sourceIds(ask(token, "quartz crystals"))).containsExactly(keep));
    }

    @Test
    void updatingAnEntryReplacesItsVector() throws Exception {
        String token = newUserToken();
        String id = createEntry(token, "Lighthouse visit", "Lighthouse tour on the northern coast").get("id").asText();
        awaitIndexed(token, "lighthouse northern coast", id);

        putAs("/api/v1/entries/" + id, token, Map.of("title", "Volcano visit", "content", "Volcano crater hike"))
                .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            assertThat(sourceIds(ask(token, "volcano crater hike"))).contains(id);
            JsonNode old = ask(token, "lighthouse northern coast");
            assertThat(old.get("sources").get(0).get("title").asText()).isEqualTo("Volcano visit");
        });
    }

    @Test
    void whenTheChatModelIsDownAskReturns503WithAMessage() throws Exception {
        String token = newUserToken();
        String id = createEntry(token, "Harbour", "Fishing boats in the harbour at dawn").get("id").asText();
        awaitIndexed(token, "fishing boats harbour", id);

        CHAT_DOWN.set(true);
        try {
            perform(post("/api/v1/ask"), token, Map.of("question", "fishing boats harbour"))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            CHAT_DOWN.set(false);
        }
    }

    @Test
    void topKOutsideOneToTenIsRejected() throws Exception {
        String token = newUserToken();
        perform(post("/api/v1/ask"), token, Map.of("question", "x", "topK", 11)).andExpect(status().isBadRequest());
        perform(post("/api/v1/ask"), token, Map.of("question", "x", "topK", 0)).andExpect(status().isBadRequest());
        perform(post("/api/v1/ask"), null, Map.of("question", "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminReindexRebuildsPointsFromMongoAndRegularUsersAreForbidden() throws Exception {
        String token = newUserToken();
        String id = createEntry(token, "Windmill", "Old windmill beside the canal").get("id").asText();
        awaitIndexed(token, "windmill canal", id);

        String adminName = uniqueName("root");
        User admin = new User(adminName, adminName, encoder.encode(PASSWORD));
        admin.setRoles(EnumSet.of(Role.USER, Role.ADMIN));
        users.save(admin);
        String adminToken = login(adminName, PASSWORD).get("accessToken").asText();

        perform(post("/api/v1/admin/rag/reindex"), token, Map.of()).andExpect(status().isForbidden());
        String body = perform(post("/api/v1/admin/rag/reindex"), adminToken, Map.of())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("indexed").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(sourceIds(ask(token, "windmill canal"))).contains(id);
    }
}
