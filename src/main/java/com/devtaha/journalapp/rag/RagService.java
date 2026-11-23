package com.devtaha.journalapp.rag;

import com.devtaha.journalapp.entry.JournalEntry;
import com.devtaha.journalapp.entry.JournalEntryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Answers a question from the caller's own entries only. */
@Service
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagService {

    static final String NOT_FOUND_ANSWER = "I could not find that in your journal.";

    private final EmbeddingClient embeddings;
    private final ChatClient chat;
    private final VectorStore store;
    private final JournalEntryRepository entries;
    private final RagIndexService index;
    private final RagProperties props;

    public RagService(EmbeddingClient embeddings, ChatClient chat, VectorStore store,
                      JournalEntryRepository entries, RagIndexService index, RagProperties props) {
        this.embeddings = embeddings;
        this.chat = chat;
        this.store = store;
        this.entries = entries;
        this.index = index;
        this.props = props;
    }

    /** Missing topK means the default; anything outside 1..max is clamped. */
    int effectiveTopK(Integer requested) {
        int k = requested == null ? props.defaultTopK() : requested;
        return Math.max(1, Math.min(k, props.maxTopK()));
    }

    public AskResponse ask(String userId, String question, Integer topK) {
        float[] vector = embeddings.embed(question);
        index.ensureCollection(vector.length);
        List<VectorStore.Hit> hits = store.search(userId, vector, effectiveTopK(topK));

        // The text comes from MongoDB, scoped to the caller again, so a stale point never leaks content.
        List<JournalEntry> found = new ArrayList<>();
        List<AskResponse.Source> sources = new ArrayList<>();
        for (VectorStore.Hit hit : hits) {
            entries.findByIdAndUserId(hit.entryId(), userId).ifPresent(entry -> {
                found.add(entry);
                sources.add(new AskResponse.Source(entry.getId(), entry.getTitle(), hit.score()));
            });
        }
        if (found.isEmpty()) {
            return new AskResponse(NOT_FOUND_ANSWER, List.of());
        }
        String answer = chat.chat(PromptBuilder.SYSTEM,
                PromptBuilder.userPrompt(question, found, props.maxEntryChars()));
        return new AskResponse(answer, sources);
    }
}
