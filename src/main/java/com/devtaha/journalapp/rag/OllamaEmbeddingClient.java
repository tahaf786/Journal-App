package com.devtaha.journalapp.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** Calls {@code POST /api/embed} of the Ollama REST API. */
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient http;
    private final String model;

    public OllamaEmbeddingClient(RestClient http, String model) {
        this.http = http;
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        JsonNode body;
        try {
            body = http.post().uri("/api/embed").body(Map.of("model", model, "input", text))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new RagUnavailableException("Embedding model is not reachable", e);
        }
        JsonNode vector = body == null ? null : body.path("embeddings").path(0);
        if (vector == null || !vector.isArray() || vector.isEmpty()) {
            throw new RagUnavailableException("Embedding model returned no vector");
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = (float) vector.get(i).asDouble();
        }
        return result;
    }
}
