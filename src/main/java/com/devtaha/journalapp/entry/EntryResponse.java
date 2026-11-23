package com.devtaha.journalapp.entry;

import java.time.Instant;

public record EntryResponse(String id, String title, String content, Instant date, Instant updatedAt) {

    public static EntryResponse from(JournalEntry entry) {
        return new EntryResponse(entry.getId(), entry.getTitle(), entry.getContent(), entry.getDate(),
                entry.getUpdatedAt());
    }
}
