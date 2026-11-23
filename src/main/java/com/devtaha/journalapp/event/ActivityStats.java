package com.devtaha.journalapp.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Per-user summary built by the Kafka consumer. The document id is the user id. */
@Document(collection = "activity_stats")
@Getter
@Setter
@NoArgsConstructor
public class ActivityStats {

    @Id
    private String userId;

    private long entryCount;

    private Instant lastEntryDate;

    private int streak;

    public ActivityStats(String userId) {
        this.userId = userId;
    }
}
