package com.devtaha.journalapp.rag;

import com.devtaha.journalapp.entry.JournalEntry;
import com.devtaha.journalapp.entry.JournalEntryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps the vector index in step with MongoDB, which stays the source of truth. */
@Service
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true")
public class RagIndexService {

    private static final int BATCH = 50;

    private final JournalEntryRepository entries;
    private final EmbeddingClient embeddings;
    private final VectorStore store;
    private final RagProperties props;
    private final AtomicBoolean collectionReady = new AtomicBoolean(false);

    public RagIndexService(JournalEntryRepository entries, EmbeddingClient embeddings, VectorStore store,
                           RagProperties props) {
        this.entries = entries;
        this.embeddings = embeddings;
        this.store = store;
        this.props = props;
    }

    /** Creates the collection on first use, sized to whatever the embedding model returns. */
    void ensureCollection(int dimension) {
        if (!collectionReady.get()) {
            store.ensureCollection(dimension);
            collectionReady.set(true);
        }
    }

    /** Embeds the current state of an entry and upserts its point. Safe to repeat. */
    public void index(JournalEntry entry) {
        float[] vector = embeddings.embed(PromptBuilder.entryText(entry.getTitle(), entry.getContent(),
                props.maxEntryChars()));
        ensureCollection(vector.length);
        store.upsert(new VectorStore.IndexedEntry(entry.getUserId(), entry.getId(), entry.getTitle(),
                entry.getDate()), vector);
    }

    /** Index the entry if it still exists in MongoDB, otherwise remove its point. */
    public void sync(String entryId) {
        Optional<JournalEntry> entry = entries.findById(entryId);
        if (entry.isPresent()) {
            index(entry.get());
        } else {
            remove(entryId);
        }
    }

    public void remove(String entryId) {
        store.delete(entryId);
    }

    /** Rebuilds points from MongoDB, for one user or for everyone. Returns the number of entries indexed. */
    public int reindex(String userId) {
        int count = 0;
        int page = 0;
        Page<JournalEntry> batch;
        do {
            PageRequest request = PageRequest.of(page++, BATCH, Sort.by("id"));
            batch = userId == null ? entries.findAll(request) : entries.findByUserId(userId, request);
            for (JournalEntry entry : batch) {
                index(entry);
                count++;
            }
        } while (batch.hasNext());
        return count;
    }
}
