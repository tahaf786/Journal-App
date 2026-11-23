package com.devtaha.journalapp.event;

import com.devtaha.journalapp.config.AppProperties;
import com.devtaha.journalapp.entry.JournalEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component
public class EntryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EntryEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;
    private final String topic;
    private final Clock clock;

    public EntryEventPublisher(KafkaTemplate<String, Object> kafka, AppProperties props, Clock clock) {
        this.kafka = kafka;
        this.topic = props.kafka().entryEventsTopic();
        this.clock = clock;
    }

    /** Sends asynchronously. A failed send is logged and does not fail the API request. */
    public void publish(EntryEventType type, JournalEntry entry) {
        EntryEvent event = new EntryEvent(UUID.randomUUID().toString(), type, entry.getId(), entry.getUserId(),
                entry.getDate(), clock.instant());
        kafka.send(topic, event.userId(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} event for entry {}", type, entry.getId(), error);
            }
        });
    }
}
