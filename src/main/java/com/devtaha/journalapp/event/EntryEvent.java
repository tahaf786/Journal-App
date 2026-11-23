package com.devtaha.journalapp.event;

import java.time.Instant;

/** Published to Kafka keyed by userId, so one user's events stay in order on one partition. */
public record EntryEvent(String eventId, EntryEventType type, String entryId, String userId, Instant entryDate,
                         Instant occurredAt) {
}
