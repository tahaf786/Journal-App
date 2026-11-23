package com.devtaha.journalapp.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Qdrant REST API client: one point per entry, cosine distance, keyword index on userId. */
public class QdrantVectorStore implements VectorStore {

    private final RestClient http;
    private final String collection;

    public QdrantVectorStore(RestClient http, String collection) {
        this.http = http;
        this.collection = collection;
    }

    /** Qdrant ids must be unsigned ints or UUIDs; a name-based UUID makes upserts idempotent per entry. */
    static String pointId(String entryId) {
        return UUID.nameUUIDFromBytes(entryId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** The mandatory per-user condition of every search. */
    static Map<String, Object> userFilter(String userId) {
        return Map.of("must", List.of(Map.of("key", "userId", "match", Map.of("value", userId))));
    }

    @Override
    public void ensureCollection(int dimension) {
        call(() -> {
            try {
                http.get().uri("/collections/{c}", collection).retrieve().toBodilessEntity();
            } catch (HttpClientErrorException.NotFound e) {
                http.put().uri("/collections/{c}", collection)
                        .body(Map.of("vectors", Map.of("size", dimension, "distance", "Cosine"))).retrieve()
                        .toBodilessEntity();
                http.put().uri("/collections/{c}/index?wait=true", collection)
                        .body(Map.of("field_name", "userId", "field_schema", "keyword")).retrieve()
                        .toBodilessEntity();
            }
            return null;
        });
    }

    @Override
    public void upsert(IndexedEntry entry, float[] vector) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", entry.userId());
        payload.put("entryId", entry.entryId());
        payload.put("title", entry.title());
        payload.put("date", entry.date() == null ? null : entry.date().toString());
        Map<String, Object> point = Map.of("id", pointId(entry.entryId()), "vector", vector, "payload", payload);
        call(() -> http.put().uri("/collections/{c}/points?wait=true", collection)
                .body(Map.of("points", List.of(point))).retrieve().toBodilessEntity());
    }

    @Override
    public void delete(String entryId) {
        try {
            call(() -> http.post().uri("/collections/{c}/points/delete?wait=true", collection)
                    .body(Map.of("points", List.of(pointId(entryId)))).retrieve().toBodilessEntity());
        } catch (RagUnavailableException e) {
            // A collection that does not exist yet holds no point to remove.
            if (!(e.getCause() instanceof HttpClientErrorException.NotFound)) {
                throw e;
            }
        }
    }

    @Override
    public List<Hit> search(String userId, float[] vector, int limit) {
        Map<String, Object> request = Map.of("vector", vector, "limit", limit, "with_payload", true,
                "filter", userFilter(userId));
        JsonNode body = call(() -> http.post().uri("/collections/{c}/points/search", collection).body(request)
                .retrieve().body(JsonNode.class));
        List<Hit> hits = new ArrayList<>();
        for (JsonNode hit : body.path("result")) {
            JsonNode payload = hit.path("payload");
            String date = payload.path("date").isNull() ? null : payload.path("date").asText(null);
            hits.add(new Hit(payload.path("entryId").asText(), payload.path("title").asText(),
                    date == null ? null : Instant.parse(date), hit.path("score").asDouble()));
        }
        return hits;
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientException e) {
            throw new RagUnavailableException("Vector store is not reachable", e);
        }
    }
}
