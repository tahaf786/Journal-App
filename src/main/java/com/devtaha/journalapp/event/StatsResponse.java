package com.devtaha.journalapp.event;

import java.time.Instant;

public record StatsResponse(long entryCount, Instant lastEntryDate, int currentStreakDays) {
}
