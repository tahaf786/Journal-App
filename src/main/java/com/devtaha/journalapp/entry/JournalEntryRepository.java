package com.devtaha.journalapp.entry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface JournalEntryRepository extends MongoRepository<JournalEntry, String>, JournalEntryRepositoryCustom {

    Optional<JournalEntry> findByIdAndUserId(String id, String userId);

    Page<JournalEntry> findByUserId(String userId, Pageable pageable);

    long deleteByUserId(String userId);
}
