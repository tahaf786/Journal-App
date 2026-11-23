package com.devtaha.journalapp.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies entry events to the per-user summary a poll at a time. One transaction covers every event of a poll, so
 * the cost of a commit is shared. If the batch fails, the events are retried one by one so that only the record
 * that really fails is retried and dead-lettered by the error handler.
 */
@Component
public class EntryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntryEventConsumer.class);

    private final StatsService stats;

    public EntryEventConsumer(StatsService stats) {
        this.stats = stats;
    }

    @KafkaListener(topics = "${app.kafka.entry-events-topic}", containerFactory = "statsBatchListenerFactory")
    public void onEvents(List<ConsumerRecord<String, EntryEvent>> records) {
        List<EntryEvent> events = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, EntryEvent> record = records.get(i);
            if (record.value() == null) {
                // A message that could not be deserialized: apply what came before it, then hand this record to
                // the error handler, which retries it and sends it to the dead-letter topic.
                applyBatch(records.subList(0, i), events);
                throw new BatchListenerFailedException("Undeserializable record", i);
            }
            events.add(record.value());
        }
        applyBatch(records, events);
    }

    private void applyBatch(List<ConsumerRecord<String, EntryEvent>> records, List<EntryEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        try {
            int skipped = stats.applyBatch(events);
            if (skipped > 0) {
                log.info("Skipped {} already processed events in a batch of {}", skipped, events.size());
            }
        } catch (RuntimeException batchFailure) {
            log.warn("Batch of {} events failed, applying one by one: {}", events.size(), batchFailure.toString());
            for (int i = 0; i < events.size(); i++) {
                try {
                    stats.applyBatch(List.of(events.get(i)));
                } catch (RuntimeException e) {
                    throw new BatchListenerFailedException("Event failed", e, i);
                }
            }
        }
    }
}
