package com.devtaha.journalapp.entry;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * The compound index serves "a user's entries, newest first". The text index
 * serves the search parameter.
 */
@Document(collection = "journal_entries")
@CompoundIndex(name = "user_date", def = "{'userId': 1, 'date': -1}")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

    @Id
    private String id;

    private String userId;

    @TextIndexed(weight = 2)
    private String title;

    @TextIndexed
    private String content;

    private Instant date;

    private Instant updatedAt;

    public JournalEntry(String userId, String title, String content, Instant date) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.date = date;
        this.updatedAt = date;
    }
}
