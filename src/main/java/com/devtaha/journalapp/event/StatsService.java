package com.devtaha.journalapp.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies entry events to the per-user summary. Days are counted in UTC.
 * A delete lowers the entry count but does not recompute the streak.
 */
@Service
public class StatsService {

    private final ActivityStatsRepository stats;
    private final ProcessedEventRepository processed;
    private final Clock clock;

    public StatsService(ActivityStatsRepository stats, ProcessedEventRepository processed, Clock clock) {
        this.stats = stats;
        this.processed = processed;
        this.clock = clock;
    }

    /**
     * Marks the event as processed and updates the summary in one transaction.
     * A repeated event id throws DuplicateKeyException and changes nothing.
     */
    @Transactional
    public void apply(EntryEvent event) {
        processed.insert(new ProcessedEvent(event.eventId(), clock.instant()));
        ActivityStats current = stats.findById(event.userId()).orElseGet(() -> new ActivityStats(event.userId()));
        applyTo(current, event);
        stats.save(current);
    }

    /**
     * Applies a poll's worth of events in one transaction and returns how many were skipped as already processed.
     * Events whose id was seen before, in an earlier batch or earlier in this one, change nothing. Events are applied
     * in the given order, so per-user ordering is kept. If anything fails the transaction rolls back completely,
     * so a retry cannot count an event twice.
     */
    @Transactional
    public int applyBatch(List<EntryEvent> events) {
        List<String> ids = events.stream().map(EntryEvent::eventId).distinct().toList();
        Set<String> known = new HashSet<>();
        processed.findAllById(ids).forEach(p -> known.add(p.getEventId()));

        List<EntryEvent> fresh = new ArrayList<>(events.size());
        List<ProcessedEvent> markers = new ArrayList<>(events.size());
        Instant now = clock.instant();
        for (EntryEvent event : events) {
            if (!known.add(event.eventId())) {
                continue;
            }
            fresh.add(event);
            markers.add(new ProcessedEvent(event.eventId(), now));
        }
        if (fresh.isEmpty()) {
            return events.size();
        }
        processed.insert(markers);

        Map<String, ActivityStats> byUser = new LinkedHashMap<>();
        stats.findAllById(fresh.stream().map(EntryEvent::userId).distinct().toList())
                .forEach(s -> byUser.put(s.getUserId(), s));
        for (EntryEvent event : fresh) {
            applyTo(byUser.computeIfAbsent(event.userId(), ActivityStats::new), event);
        }
        stats.saveAll(byUser.values());
        return events.size() - fresh.size();
    }

    private void applyTo(ActivityStats current, EntryEvent event) {
        switch (event.type()) {
            case CREATED -> applyCreated(current, event.entryDate());
            case DELETED -> current.setEntryCount(Math.max(0, current.getEntryCount() - 1));
            case UPDATED -> { }
        }
    }

    public StatsResponse get(String userId) {
        ActivityStats current = stats.findById(userId).orElseGet(() -> new ActivityStats(userId));
        return new StatsResponse(current.getEntryCount(), current.getLastEntryDate(), activeStreak(current));
    }

    private void applyCreated(ActivityStats current, Instant entryDate) {
        current.setEntryCount(current.getEntryCount() + 1);
        if (current.getLastEntryDate() == null) {
            current.setStreak(1);
            current.setLastEntryDate(entryDate);
            return;
        }
        long gap = ChronoUnit.DAYS.between(day(current.getLastEntryDate()), day(entryDate));
        if (gap == 1) {
            current.setStreak(current.getStreak() + 1);
        } else if (gap > 1) {
            current.setStreak(1);
        }
        if (entryDate.isAfter(current.getLastEntryDate())) {
            current.setLastEntryDate(entryDate);
        }
    }

    /** The streak still counts if the last entry was today or yesterday. */
    private int activeStreak(ActivityStats current) {
        if (current.getLastEntryDate() == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(clock);
        long idleDays = ChronoUnit.DAYS.between(day(current.getLastEntryDate()), today);
        return idleDays <= 1 ? current.getStreak() : 0;
    }

    private static LocalDate day(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
