package com.devtaha.journalapp.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings for the "ask my journal" feature. Everything is inert while {@code enabled} is false. */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(@DefaultValue("false") boolean enabled,
                            @DefaultValue("http://localhost:11434") String ollamaUrl,
                            @DefaultValue("http://localhost:6333") String qdrantUrl,
                            @DefaultValue("nomic-embed-text") String embedModel,
                            @DefaultValue("llama3.2:1b") String chatModel,
                            @DefaultValue("journal_entries") String collection,
                            @DefaultValue("10s") Duration embedTimeout,
                            @DefaultValue("120s") Duration chatTimeout,
                            @DefaultValue("5s") Duration qdrantTimeout,
                            @DefaultValue("4") int defaultTopK,
                            @DefaultValue("10") int maxTopK,
                            @DefaultValue("4000") int maxEntryChars) {
}
