package com.devtaha.journalapp.entry;

import com.devtaha.journalapp.event.EntryEventPublisher;
import com.devtaha.journalapp.event.EntryEventType;
import com.devtaha.journalapp.exception.BadRequestException;
import com.devtaha.journalapp.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalEntryServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T08:00:00Z");

    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private final EntryEventPublisher events = mock(EntryEventPublisher.class);
    private JournalEntryService service;

    @BeforeEach
    void setUp() {
        service = new JournalEntryService(entries, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JournalEntry stored() {
        JournalEntry entry = new JournalEntry("u1", "Title", "Body", NOW);
        entry.setId("e1");
        return entry;
    }

    @Test
    void createDefaultsTheDateAndPublishesAnEvent() {
        when(entries.save(any(JournalEntry.class))).thenAnswer(call -> {
            JournalEntry entry = call.getArgument(0);
            entry.setId("e1");
            return entry;
        });

        EntryResponse response = service.create("u1", new EntryRequest("Title", "Body", null));

        assertThat(response.id()).isEqualTo("e1");
        assertThat(response.date()).isEqualTo(NOW);
        verify(events).publish(eq(EntryEventType.CREATED), any(JournalEntry.class));
    }

    @Test
    void createKeepsTheSuppliedDate() {
        Instant supplied = Instant.parse("2026-02-01T00:00:00Z");
        when(entries.save(any(JournalEntry.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.create("u1", new EntryRequest("T", null, supplied)).date()).isEqualTo(supplied);
    }

    @Test
    void getIsScopedToTheOwner() {
        when(entries.findByIdAndUserId("e1", "u1")).thenReturn(Optional.of(stored()));
        when(entries.findByIdAndUserId("e1", "other")).thenReturn(Optional.empty());

        assertThat(service.get("u1", "e1").title()).isEqualTo("Title");
        assertThatThrownBy(() -> service.get("other", "e1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesFieldsAndPublishesAnEvent() {
        when(entries.findByIdAndUserId("e1", "u1")).thenReturn(Optional.of(stored()));
        when(entries.save(any(JournalEntry.class))).thenAnswer(call -> call.getArgument(0));

        EntryResponse response = service.update("u1", "e1", new EntryRequest("New", "Changed", null));

        assertThat(response.title()).isEqualTo("New");
        assertThat(response.content()).isEqualTo("Changed");
        assertThat(response.date()).isEqualTo(NOW);
        verify(events).publish(eq(EntryEventType.UPDATED), any(JournalEntry.class));
    }

    @Test
    void updateOfAnotherUsersEntryFailsWithoutSideEffects() {
        when(entries.findByIdAndUserId("e1", "other")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("other", "e1", new EntryRequest("x", "y", null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(entries, never()).save(any());
        verify(events, never()).publish(any(EntryEventType.class), any(JournalEntry.class));
    }

    @Test
    void deleteRemovesTheEntryAndPublishesAnEvent() {
        JournalEntry entry = stored();
        when(entries.findByIdAndUserId("e1", "u1")).thenReturn(Optional.of(entry));

        service.delete("u1", "e1");

        verify(entries).delete(entry);
        verify(events).publish(EntryEventType.DELETED, entry);
    }

    @Test
    void listWithoutQueryUsesThePlainRepositoryQuery() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("date"));
        when(entries.findByUserId("u1", pageable)).thenReturn(new PageImpl<>(List.of(stored()), pageable, 1));

        PageResponse<EntryResponse> page = service.list("u1", " ", pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
        verify(entries, never()).search(any(), any(), any());
    }

    @Test
    void listWithQueryUsesFullTextSearch() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(entries.search("u1", "trip", pageable)).thenReturn(new PageImpl<>(List.of(stored()), pageable, 1));

        assertThat(service.list("u1", "trip", pageable).content()).hasSize(1);
    }

    @Test
    void listRejectsUnknownSortFields() {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("passwordHash"));

        assertThatThrownBy(() -> service.list("u1", null, pageable)).isInstanceOf(BadRequestException.class);
    }
}
