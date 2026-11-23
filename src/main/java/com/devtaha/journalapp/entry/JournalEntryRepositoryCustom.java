package com.devtaha.journalapp.entry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JournalEntryRepositoryCustom {

    /** Entries of one user matching the full-text query, paged and sorted by the pageable. */
    Page<JournalEntry> search(String userId, String query, Pageable pageable);
}
