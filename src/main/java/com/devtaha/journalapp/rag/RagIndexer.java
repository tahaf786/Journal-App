package com.devtaha.journalapp.rag;

import com.devtaha.journalapp.event.EntryEvent;
import com.devtaha.journalapp.event.EntryEventType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Second consumer group on the entry topic. Failures are rethrown so the shared error handler retries and then
 * dead-letters the record. Re-processing is harmless: points are keyed by entry id.
 */
@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagIndexer {

    private final RagIndexService index;

    public RagIndexer(RagIndexService index) {
        this.index = index;
    }

    @KafkaListener(topics = "${app.kafka.entry-events-topic}", groupId = "journal-rag")
    public void onEvent(EntryEvent event) {
        if (event.type() == EntryEventType.DELETED) {
            index.remove(event.entryId());
        } else {
            index.sync(event.entryId());
        }
    }
}
