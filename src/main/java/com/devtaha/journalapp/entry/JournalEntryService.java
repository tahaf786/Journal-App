package com.devtaha.journalapp.entry;

import com.devtaha.journalapp.config.CacheConfig;
import com.devtaha.journalapp.event.EntryEventPublisher;
import com.devtaha.journalapp.event.EntryEventType;
import com.devtaha.journalapp.exception.BadRequestException;
import com.devtaha.journalapp.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/**
 * Every query is scoped by the owner's id, so another user's entry is simply "not found".
 * Reads are cached; writes evict the affected keys and publish an event.
 */
@Service
public class JournalEntryService {

    private static final Set<String> SORTABLE = Set.of("date", "title");

    private final JournalEntryRepository entries;
    private final EntryEventPublisher events;
    private final Clock clock;

    public JournalEntryService(JournalEntryRepository entries, EntryEventPublisher events, Clock clock) {
        this.entries = entries;
        this.events = events;
        this.clock = clock;
    }

    @CacheEvict(cacheNames = CacheConfig.ENTRY_PAGES, allEntries = true)
    public EntryResponse create(String userId, EntryRequest request) {
        Instant date = request.date() != null ? request.date() : clock.instant();
        JournalEntry saved = entries.save(new JournalEntry(userId, request.title(), request.content(), date));
        events.publish(EntryEventType.CREATED, saved);
        return EntryResponse.from(saved);
    }

    @Cacheable(cacheNames = CacheConfig.ENTRIES, key = "#userId + ':' + #id")
    public EntryResponse get(String userId, String id) {
        return EntryResponse.from(find(userId, id));
    }

    @Cacheable(cacheNames = CacheConfig.ENTRY_PAGES,
            key = "#userId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort"
                    + " + ':' + (#query ?: '')")
    public PageResponse<EntryResponse> list(String userId, String query, Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty() + "'; use date or title");
            }
        });
        Page<JournalEntry> page = (query == null || query.isBlank())
                ? entries.findByUserId(userId, pageable)
                : entries.search(userId, query, pageable);
        return PageResponse.from(page, EntryResponse::from);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ENTRIES, key = "#userId + ':' + #id"),
            @CacheEvict(cacheNames = CacheConfig.ENTRY_PAGES, allEntries = true)})
    public EntryResponse update(String userId, String id, EntryRequest request) {
        JournalEntry entry = find(userId, id);
        entry.setTitle(request.title());
        entry.setContent(request.content());
        if (request.date() != null) {
            entry.setDate(request.date());
        }
        entry.setUpdatedAt(clock.instant());
        JournalEntry saved = entries.save(entry);
        events.publish(EntryEventType.UPDATED, saved);
        return EntryResponse.from(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ENTRIES, key = "#userId + ':' + #id"),
            @CacheEvict(cacheNames = CacheConfig.ENTRY_PAGES, allEntries = true)})
    public void delete(String userId, String id) {
        JournalEntry entry = find(userId, id);
        entries.delete(entry);
        events.publish(EntryEventType.DELETED, entry);
    }

    private JournalEntry find(String userId, String id) {
        return entries.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found"));
    }
}
