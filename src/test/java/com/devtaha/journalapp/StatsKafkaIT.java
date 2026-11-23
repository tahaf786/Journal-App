package com.devtaha.journalapp;

import com.devtaha.journalapp.event.EntryEvent;
import com.devtaha.journalapp.event.EntryEventType;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsKafkaIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    KafkaTemplate<String, Object> kafka;

    @Value("${app.kafka.entry-events-topic}")
    String topic;

    @Value("${app.kafka.dead-letter-topic}")
    String deadLetterTopic;

    private JsonNode stats(String token) throws Exception {
        return getJson("/api/v1/users/me/stats", token);
    }

    private String userIdOf(String token) throws Exception {
        return getJson("/api/v1/users/me", token).get("id").asText();
    }

    @Test
    void statsStartEmpty() throws Exception {
        String token = newUserToken();

        JsonNode stats = stats(token);

        assertThat(stats.get("entryCount").asLong()).isZero();
        assertThat(stats.get("currentStreakDays").asInt()).isZero();
    }

    @Test
    void entryEventsUpdateTheSummary() throws Exception {
        String token = newUserToken();
        createEntry(token, "One", "1");
        String second = createEntry(token, "Two", "2").get("id").asText();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode stats = stats(token);
            assertThat(stats.get("entryCount").asLong()).isEqualTo(2);
            assertThat(stats.get("lastEntryDate").isNull()).isFalse();
            assertThat(stats.get("currentStreakDays").asInt()).isEqualTo(1);
        });

        deleteAs("/api/v1/entries/" + second, token).andExpect(status().isNoContent());

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(stats(token).get("entryCount").asLong()).isEqualTo(1));
    }

    @Test
    void consecutiveDaysBuildAStreak() throws Exception {
        String token = newUserToken();
        Instant now = Instant.now();
        createEntry(token, Map.of("title", "Two days ago", "date", now.minus(2, ChronoUnit.DAYS).toString()));
        createEntry(token, Map.of("title", "Yesterday", "date", now.minus(1, ChronoUnit.DAYS).toString()));
        createEntry(token, Map.of("title", "Today", "date", now.toString()));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode stats = stats(token);
            assertThat(stats.get("entryCount").asLong()).isEqualTo(3);
            assertThat(stats.get("currentStreakDays").asInt()).isEqualTo(3);
        });
    }

    @Test
    void streakIsNotActiveWhenTheLastEntryIsOld() throws Exception {
        String token = newUserToken();
        createEntry(token, Map.of("title", "Long ago", "date", Instant.now().minus(10, ChronoUnit.DAYS).toString()));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode stats = stats(token);
            assertThat(stats.get("entryCount").asLong()).isEqualTo(1);
            assertThat(stats.get("currentStreakDays").asInt()).isZero();
        });
    }

    @Test
    void duplicateEventIdsAreAppliedOnce() throws Exception {
        String token = newUserToken();
        String userId = userIdOf(token);
        EntryEvent first = event(userId, UUID.randomUUID().toString());
        EntryEvent distinct = event(userId, UUID.randomUUID().toString());

        kafka.send(topic, userId, first).get();
        kafka.send(topic, userId, first).get();
        kafka.send(topic, userId, distinct).get();

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(stats(token).get("entryCount").asLong()).isEqualTo(2));
    }

    @Test
    void poisonMessageEndsUpOnTheDeadLetterTopic() throws Exception {
        String marker = "not-json-" + UUID.randomUUID();
        kafka.send(topic, "poison-key", marker.getBytes()).get();

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(deadLetterTopic));
            await().atMost(TIMEOUT).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (marker.equals(record.value())) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    @Test
    void aBurstAcrossUsersAndPartitionsIsCountedExactlyOnce() throws Exception {
        List<String> tokens = new java.util.ArrayList<>();
        List<String> userIds = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String token = newUserToken();
            tokens.add(token);
            userIds.add(userIdOf(token));
        }
        int perUser = 120;
        for (int n = 0; n < perUser; n++) {
            for (String userId : userIds) {
                EntryEvent event = event(userId, UUID.randomUUID().toString());
                kafka.send(topic, userId, event);
                if (n % 10 == 0) {
                    kafka.send(topic, userId, event);
                }
            }
        }
        kafka.flush();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            for (String token : tokens) {
                assertThat(stats(token).get("entryCount").asLong()).isEqualTo(perUser);
            }
        });
        Thread.sleep(1500);
        for (String token : tokens) {
            assertThat(stats(token).get("entryCount").asLong()).isEqualTo(perUser);
        }
    }

    @Test
    void poisonMessageAmongGoodEventsDoesNotStopTheRest() throws Exception {
        String token = newUserToken();
        String userId = userIdOf(token);
        String marker = "not-json-" + UUID.randomUUID();
        kafka.send(topic, userId, event(userId, UUID.randomUUID().toString()));
        kafka.send(topic, userId, marker.getBytes());
        kafka.send(topic, userId, event(userId, UUID.randomUUID().toString()));
        kafka.send(topic, userId, event(userId, UUID.randomUUID().toString())).get();

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(stats(token).get("entryCount").asLong()).isEqualTo(3));

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(deadLetterTopic));
            await().atMost(TIMEOUT).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (marker.equals(record.value())) {
                        return true;
                    }
                }
                return false;
            });
        }
        assertThat(stats(token).get("entryCount").asLong()).isEqualTo(3);
    }

    private EntryEvent event(String userId, String eventId) {
        return new EntryEvent(eventId, EntryEventType.CREATED, UUID.randomUUID().toString(), userId,
                Instant.now(), Instant.now());
    }
}
