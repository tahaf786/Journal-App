package com.devtaha.journalapp.rag;

import com.devtaha.journalapp.entry.JournalEntry;
import com.devtaha.journalapp.entry.JournalEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final ChatClient chat = mock(ChatClient.class);
    private final VectorStore store = mock(VectorStore.class);
    private final JournalEntryRepository entries = mock(JournalEntryRepository.class);
    private RagService service;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties(true, "http://o", "http://q", "embed", "chat", "c",
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 4, 10, 4000);
        RagIndexService index = new RagIndexService(entries, embeddings, store, props);
        service = new RagService(embeddings, chat, store, entries, index, props);
        when(embeddings.embed(anyString())).thenReturn(new float[]{1f, 0f});
    }

    private JournalEntry entry(String id, String userId, String title) {
        JournalEntry e = new JournalEntry(userId, title, "content of " + title, Instant.parse("2026-01-01T00:00:00Z"));
        e.setId(id);
        return e;
    }

    @Test
    void topKDefaultsToFourAndIsClampedToTheConfiguredRange() {
        assertThat(service.effectiveTopK(null)).isEqualTo(4);
        assertThat(service.effectiveTopK(7)).isEqualTo(7);
        assertThat(service.effectiveTopK(0)).isEqualTo(1);
        assertThat(service.effectiveTopK(-3)).isEqualTo(1);
        assertThat(service.effectiveTopK(99)).isEqualTo(10);
    }

    @Test
    void searchAlwaysCarriesTheCallersUserIdAndTheEffectiveLimit() {
        when(store.search(anyString(), any(), anyInt())).thenReturn(List.of());

        service.ask("alice", "anything?", 99);

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(store).search(user.capture(), any(), limit.capture());
        assertThat(user.getValue()).isEqualTo("alice");
        assertThat(limit.getValue()).isEqualTo(10);
    }

    @Test
    void answersFromTheRetrievedEntriesAndReturnsTheSources() {
        when(store.search(eq("alice"), any(), eq(4))).thenReturn(List.of(
                new VectorStore.Hit("e1", "Hike", null, 0.91), new VectorStore.Hit("e2", "Bread", null, 0.55)));
        when(entries.findByIdAndUserId("e1", "alice")).thenReturn(Optional.of(entry("e1", "alice", "Hike")));
        when(entries.findByIdAndUserId("e2", "alice")).thenReturn(Optional.of(entry("e2", "alice", "Bread")));
        when(chat.chat(anyString(), anyString())).thenReturn("You hiked to the lake.");

        AskResponse response = service.ask("alice", "Where did I hike?", null);

        assertThat(response.answer()).isEqualTo("You hiked to the lake.");
        assertThat(response.sources()).extracting(AskResponse.Source::entryId).containsExactly("e1", "e2");
        assertThat(response.sources().get(0).score()).isEqualTo(0.91);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chat).chat(eq(PromptBuilder.SYSTEM), prompt.capture());
        assertThat(prompt.getValue()).contains("content of Hike", "content of Bread", "Where did I hike?");
    }

    @Test
    void aStalePointWhoseEntryIsGoneOrNotTheCallersIsDropped() {
        when(store.search(eq("alice"), any(), anyInt())).thenReturn(List.of(
                new VectorStore.Hit("gone", "Old", null, 0.9), new VectorStore.Hit("e1", "Hike", null, 0.5)));
        when(entries.findByIdAndUserId("gone", "alice")).thenReturn(Optional.empty());
        when(entries.findByIdAndUserId("e1", "alice")).thenReturn(Optional.of(entry("e1", "alice", "Hike")));
        when(chat.chat(anyString(), anyString())).thenReturn("ok");

        AskResponse response = service.ask("alice", "q", null);

        assertThat(response.sources()).extracting(AskResponse.Source::entryId).containsExactly("e1");
    }

    @Test
    void noHitsMeansNoModelCallAndAnHonestAnswer() {
        when(store.search(anyString(), any(), anyInt())).thenReturn(List.of());

        AskResponse response = service.ask("alice", "q", null);

        assertThat(response.answer()).isEqualTo(RagService.NOT_FOUND_ANSWER);
        assertThat(response.sources()).isEmpty();
        verify(chat, never()).chat(anyString(), anyString());
    }

    @Test
    void unavailableEmbeddingModelSurfacesAsRagUnavailable() {
        when(embeddings.embed(anyString())).thenThrow(new RagUnavailableException("down"));

        assertThatThrownBy(() -> service.ask("alice", "q", null)).isInstanceOf(RagUnavailableException.class);
        verify(store, never()).search(anyString(), any(), anyInt());
    }

    @Test
    void unavailableVectorStoreAndChatModelSurfaceAsRagUnavailable() {
        when(store.search(anyString(), any(), anyInt())).thenThrow(new RagUnavailableException("qdrant down"));
        assertThatThrownBy(() -> service.ask("alice", "q", null)).isInstanceOf(RagUnavailableException.class);

        doReturn(List.of(new VectorStore.Hit("e1", "t", null, 1))).when(store).search(anyString(), any(), anyInt());
        when(entries.findByIdAndUserId("e1", "alice")).thenReturn(Optional.of(entry("e1", "alice", "t")));
        when(chat.chat(anyString(), anyString())).thenThrow(new RagUnavailableException("ollama down"));
        assertThatThrownBy(() -> service.ask("alice", "q", null)).isInstanceOf(RagUnavailableException.class);
    }
}
