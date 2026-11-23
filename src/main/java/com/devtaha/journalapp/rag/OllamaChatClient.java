package com.devtaha.journalapp.rag;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Calls {@code POST /api/chat} of the Ollama REST API without streaming and with temperature 0. */
public class OllamaChatClient implements ChatClient {

    private final RestClient http;
    private final String model;

    public OllamaChatClient(RestClient http, String model) {
        this.http = http;
        this.model = model;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "stream", false,
                "options", Map.of("temperature", 0),
                "messages", List.of(Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        JsonNode body;
        try {
            body = http.post().uri("/api/chat").body(request).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new RagUnavailableException("Chat model is not reachable", e);
        }
        String answer = body == null ? "" : body.path("message").path("content").asText("");
        if (answer.isBlank()) {
            throw new RagUnavailableException("Chat model returned an empty answer");
        }
        return answer.trim();
    }
}
