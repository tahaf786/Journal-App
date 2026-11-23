package com.devtaha.journalapp.rag;

import java.time.Instant;
import java.util.List;

/** Vector index for journal entries. Every search is scoped to one user. */
public interface VectorStore {

    /** Creates the collection and the userId payload index if they do not exist yet. */
    void ensureCollection(int dimension);

    /** Inserts or replaces the point of an entry; the point id is derived from the entry id. */
    void upsert(IndexedEntry entry, float[] vector);

    void delete(String entryId);

    /** Nearest entries of {@code userId} only; the user filter is part of the vector query itself. */
    List<Hit> search(String userId, float[] vector, int limit);

    record IndexedEntry(String userId, String entryId, String title, Instant date) {
    }

    record Hit(String entryId, String title, Instant date, double score) {
    }
}
