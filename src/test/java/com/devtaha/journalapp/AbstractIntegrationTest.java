package com.devtaha.journalapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.kafka.KafkaContainer;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts MongoDB (replica set), Redis and Kafka once for the whole test run and
 * boots the full application against them.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);
    static final KafkaContainer KAFKA = new StableKafkaContainer("apache/kafka:3.9.1")
            .withEnv("KAFKA_HEAP_OPTS", "-Xmx512m -Xms256m");

    static {
        MONGO.start();
        REDIS.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("journal_test"));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.jwt.secret", () -> "test-only-signing-secret-0123456789abcdef");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected void register(String username) throws Exception {
        perform(post("/api/v1/auth/register"), null,
                Map.of("username", username, "password", PASSWORD)).andExpect(status().isCreated());
    }

    protected JsonNode login(String username, String password) throws Exception {
        String body = perform(post("/api/v1/auth/login"), null, Map.of("username", username, "password", password))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    /** Registers a fresh user and returns its access token. */
    protected String newUserToken() throws Exception {
        String username = uniqueName("user");
        register(username);
        return login(username, PASSWORD).get("accessToken").asText();
    }

    protected JsonNode createEntry(String token, String title, String content) throws Exception {
        return createEntry(token, Map.of("title", title, "content", content));
    }

    protected JsonNode createEntry(String token, Map<String, Object> body) throws Exception {
        String response = perform(post("/api/v1/entries"), token, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    protected JsonNode getJson(String url, String token) throws Exception {
        String response = mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    protected ResultActions getAs(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    protected ResultActions putAs(String url, String token, Object body) throws Exception {
        return perform(put(url), token, body);
    }

    protected ResultActions deleteAs(String url, String token) throws Exception {
        return mvc.perform(delete(url).header("Authorization", "Bearer " + token));
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body)
            throws Exception {
        request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(request);
    }
}
