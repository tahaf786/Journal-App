package com.devtaha.journalapp.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Marks an event id as applied. Inserting the same id twice fails, which is what makes the consumer idempotent. */
@Document(collection = "processed_events")
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    private String eventId;

    @Indexed(expireAfter = "7d")
    private Instant processedAt;

    public ProcessedEvent(String eventId, Instant processedAt) {
        this.eventId = eventId;
        this.processedAt = processedAt;
    }
}
