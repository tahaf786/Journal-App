package com.devtaha.journalapp.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServiceBatchTest {

    private static final Instant NOW = Instant.parse("2026-05-10T12:00:00Z");

    private final ActivityStatsRepository stats = mock(ActivityStatsRepository.class);
    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final Map<String, ActivityStats> store = new HashMap<>();
    private final Set<String> processedIds = new HashSet<>();
    private StatsService service;

    @BeforeEach
    void setUp() {
        when(processed.findAllById(anyIterable())).thenAnswer(call -> {
            List<ProcessedEvent> found = new ArrayList<>();
            for (String id : call.<Iterable<String>>getArgument(0)) {
                if (processedIds.contains(id)) {
                    found.add(new ProcessedEvent(id, NOW));
                }
            }
            return found;
        });
        when(processed.insert(anyList())).thenAnswer(call -> {
            List<ProcessedEvent> markers = call.getArgument(0);
            for (ProcessedEvent marker : markers) {
                if (processedIds.contains(marker.getEventId())) {
                    throw new DuplicateKeyException("duplicate");
                }
            }
            markers.forEach(m -> processedIds.add(m.getEventId()));
            return markers;
        });
        when(stats.findAllById(anyIterable())).thenAnswer(call -> {
            List<ActivityStats> found = new ArrayList<>();
            for (String id : call.<Iterable<String>>getArgument(0)) {
                if (store.containsKey(id)) {
                    found.add(store.get(id));
                }
            }
            return found;
        });
        when(stats.saveAll(anyIterable())).thenAnswer(call -> {
            List<ActivityStats> saved = new ArrayList<>();
            for (ActivityStats s : call.<Iterable<ActivityStats>>getArgument(0)) {
                store.put(s.getUserId(), s);
                saved.add(s);
            }
            return saved;
        });
        service = new StatsService(stats, processed, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EntryEvent created(String id, String user, String date) {
        return new EntryEvent(id, EntryEventType.CREATED, "e-" + id, user, Instant.parse(date), NOW);
    }

    private static EntryEvent deleted(String id, String user) {
        return new EntryEvent(id, EntryEventType.DELETED, "e-" + id, user, null, NOW);
    }

    @Test
    void everyEventInABatchIsCountedOnce() {
        List<EntryEvent> events = List.of(
                created("a", "u1", "2026-05-10T08:00:00Z"),
                created("b", "u2", "2026-05-10T08:00:00Z"),
                created("c", "u1", "2026-05-10T09:00:00Z"),
                created("d", "u1", "2026-05-10T10:00:00Z"),
                deleted("e", "u1"));

        int skipped = service.applyBatch(events);

        assertThat(skipped).isZero();
        assertThat(store.get("u1").getEntryCount()).isEqualTo(2);
        assertThat(store.get("u2").getEntryCount()).isEqualTo(1);
        assertThat(processedIds).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
    }

    @Test
    void duplicateIdsInTheSameBatchAreAppliedOnce() {
        EntryEvent first = created("a", "u1", "2026-05-10T08:00:00Z");

        int skipped = service.applyBatch(List.of(first, first, created("b", "u1", "2026-05-10T09:00:00Z")));

        assertThat(skipped).isEqualTo(1);
        assertThat(store.get("u1").getEntryCount()).isEqualTo(2);
    }

    @Test
    void eventsAlreadyProcessedInAnEarlierBatchAreSkipped() {
        EntryEvent first = created("a", "u1", "2026-05-10T08:00:00Z");
        service.applyBatch(List.of(first));

        int skipped = service.applyBatch(List.of(first, created("b", "u1", "2026-05-10T09:00:00Z")));

        assertThat(skipped).isEqualTo(1);
        assertThat(store.get("u1").getEntryCount()).isEqualTo(2);
    }

    @Test
    void aBatchOfOnlyDuplicatesWritesNothing() {
        EntryEvent first = created("a", "u1", "2026-05-10T08:00:00Z");
        service.applyBatch(List.of(first));

        assertThat(service.applyBatch(List.of(first, first))).isEqualTo(2);

        verify(stats, times(1)).saveAll(anyIterable());
    }

    @Test
    void eventsOfOneUserKeepTheirOrder() {
        service.applyBatch(List.of(
                created("a", "u1", "2026-05-08T08:00:00Z"),
                created("b", "u1", "2026-05-09T08:00:00Z"),
                created("c", "u1", "2026-05-10T08:00:00Z")));

        assertThat(store.get("u1").getStreak()).isEqualTo(3);
    }

    @Test
    void batchGivesTheSameResultAsApplyingOneByOne() {
        List<EntryEvent> events = List.of(
                created("a", "u1", "2026-05-09T08:00:00Z"),
                created("b", "u1", "2026-05-10T08:00:00Z"),
                deleted("c", "u1"),
                created("d", "u2", "2026-05-01T08:00:00Z"));
        service.applyBatch(events);
        ActivityStats batched1 = store.get("u1");
        ActivityStats batched2 = store.get("u2");

        store.clear();
        when(stats.findById(any())).thenAnswer(call -> Optional.ofNullable(store.get(call.<String>getArgument(0))));
        when(stats.save(any(ActivityStats.class))).thenAnswer(call -> {
            ActivityStats s = call.getArgument(0);
            store.put(s.getUserId(), s);
            return s;
        });
        events.forEach(e -> service.apply(new EntryEvent("x-" + e.eventId(), e.type(), e.entryId(), e.userId(),
                e.entryDate(), e.occurredAt())));

        assertThat(store.get("u1")).usingRecursiveComparison().isEqualTo(batched1);
        assertThat(store.get("u2")).usingRecursiveComparison().isEqualTo(batched2);
    }

    @Test
    void failureWhileMarkingEventsLeavesTheSummaryUntouched() {
        when(processed.insert(anyList())).thenThrow(new DuplicateKeyException("race"));

        assertThatThrownBy(() -> service.applyBatch(List.of(created("a", "u1", "2026-05-10T08:00:00Z"))))
                .isInstanceOf(DuplicateKeyException.class);

        verify(stats, never()).saveAll(anyIterable());
        assertThat(store).isEmpty();
    }
}
