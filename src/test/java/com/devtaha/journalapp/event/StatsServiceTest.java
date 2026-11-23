package com.devtaha.journalapp.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-10T12:00:00Z");

    private final ActivityStatsRepository stats = mock(ActivityStatsRepository.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final Map<String, ActivityStats> store = new HashMap<>();
    private StatsService service;
    private int eventCounter;

    @BeforeEach
    void setUp() {
        when(stats.findById(any())).thenAnswer(call -> Optional.ofNullable(store.get(call.<String>getArgument(0))));
        when(stats.save(any(ActivityStats.class))).thenAnswer(call -> {
            ActivityStats saved = call.getArgument(0);
            store.put(saved.getUserId(), saved);
            return saved;
        });
        service = new StatsService(stats, processed, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void apply(EntryEventType type, String entryDate) {
        service.apply(new EntryEvent("ev-" + eventCounter++, type, "e", "u1",
                entryDate == null ? null : Instant.parse(entryDate), NOW));
    }

    @Test
    void firstEntryStartsAStreakOfOne() {
        apply(EntryEventType.CREATED, "2026-05-10T09:00:00Z");

        StatsResponse response = service.get("u1");

        assertThat(response.entryCount()).isEqualTo(1);
        assertThat(response.currentStreakDays()).isEqualTo(1);
        assertThat(response.lastEntryDate()).isEqualTo(Instant.parse("2026-05-10T09:00:00Z"));
    }

    @Test
    void sameDayEntriesDoNotExtendTheStreak() {
        apply(EntryEventType.CREATED, "2026-05-10T08:00:00Z");
        apply(EntryEventType.CREATED, "2026-05-10T20:00:00Z");

        assertThat(service.get("u1").entryCount()).isEqualTo(2);
        assertThat(service.get("u1").currentStreakDays()).isEqualTo(1);
    }

    @Test
    void nextDayExtendsAndAGapResetsTheStreak() {
        apply(EntryEventType.CREATED, "2026-05-07T08:00:00Z");
        apply(EntryEventType.CREATED, "2026-05-08T08:00:00Z");
        apply(EntryEventType.CREATED, "2026-05-09T08:00:00Z");
        assertThat(service.get("u1").currentStreakDays()).isEqualTo(3);

        apply(EntryEventType.CREATED, "2026-05-10T08:00:00Z");
        assertThat(service.get("u1").currentStreakDays()).isEqualTo(4);
    }

    @Test
    void gapOfMoreThanOneDayRestartsTheStreak() {
        apply(EntryEventType.CREATED, "2026-05-01T08:00:00Z");
        apply(EntryEventType.CREATED, "2026-05-02T08:00:00Z");
        apply(EntryEventType.CREATED, "2026-05-10T08:00:00Z");

        assertThat(service.get("u1").currentStreakDays()).isEqualTo(1);
    }

    @Test
    void backdatedEntryKeepsTheLastEntryDate() {
        apply(EntryEventType.CREATED, "2026-05-10T08:00:00Z");
        apply(EntryEventType.CREATED, "2026-05-01T08:00:00Z");

        StatsResponse response = service.get("u1");

        assertThat(response.entryCount()).isEqualTo(2);
        assertThat(response.lastEntryDate()).isEqualTo(Instant.parse("2026-05-10T08:00:00Z"));
        assertThat(response.currentStreakDays()).isEqualTo(1);
    }

    @Test
    void streakReadsAsZeroWhenLastEntryIsOlderThanYesterday() {
        apply(EntryEventType.CREATED, "2026-05-08T08:00:00Z");

        assertThat(service.get("u1").currentStreakDays()).isZero();
    }

    @Test
    void yesterdaysEntryKeepsTheStreakAlive() {
        apply(EntryEventType.CREATED, "2026-05-09T08:00:00Z");

        assertThat(service.get("u1").currentStreakDays()).isEqualTo(1);
    }

    @Test
    void deleteLowersTheCountButNeverBelowZero() {
        apply(EntryEventType.CREATED, "2026-05-10T08:00:00Z");
        apply(EntryEventType.DELETED, "2026-05-10T08:00:00Z");
        apply(EntryEventType.DELETED, "2026-05-10T08:00:00Z");

        assertThat(service.get("u1").entryCount()).isZero();
    }

    @Test
    void updateOnlyRecordsTheEvent() {
        apply(EntryEventType.UPDATED, "2026-05-10T08:00:00Z");

        assertThat(service.get("u1").entryCount()).isZero();
        verify(processed).insert(any(ProcessedEvent.class));
    }

    @Test
    void repeatedEventIdStopsBeforeTouchingTheSummary() {
        when(processed.insert(any(ProcessedEvent.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> apply(EntryEventType.CREATED, "2026-05-10T08:00:00Z"))
                .isInstanceOf(DuplicateKeyException.class);
        verify(stats, never()).save(any(ActivityStats.class));
    }

    @Test
    void processedMarkerUsesTheEventId() {
        apply(EntryEventType.CREATED, "2026-05-10T08:00:00Z");

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processed).insert(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo("ev-0");
    }

    @Test
    void unknownUserHasEmptyStats() {
        StatsResponse response = service.get("nobody");

        assertThat(response.entryCount()).isZero();
        assertThat(response.lastEntryDate()).isNull();
        assertThat(response.currentStreakDays()).isZero();
    }
}
