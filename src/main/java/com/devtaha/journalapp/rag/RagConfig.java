package com.devtaha.journalapp.rag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Wires the three small interfaces to plain HTTP clients. Active only when {@code app.rag.enabled=true}. */
@Configuration
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagConfig {

    private static RestClient client(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    EmbeddingClient embeddingClient(RagProperties p) {
        return new OllamaEmbeddingClient(client(p.ollamaUrl(), p.embedTimeout()), p.embedModel());
    }

    @Bean
    ChatClient chatClient(RagProperties p) {
        return new OllamaChatClient(client(p.ollamaUrl(), p.chatTimeout()), p.chatModel());
    }

    @Bean
    VectorStore vectorStore(RagProperties p) {
        return new QdrantVectorStore(client(p.qdrantUrl(), p.qdrantTimeout()), p.collection());
    }
}
