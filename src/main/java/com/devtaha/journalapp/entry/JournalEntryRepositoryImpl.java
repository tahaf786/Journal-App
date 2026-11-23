package com.devtaha.journalapp.entry;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

public class JournalEntryRepositoryImpl implements JournalEntryRepositoryCustom {

    private final MongoTemplate mongo;

    public JournalEntryRepositoryImpl(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Page<JournalEntry> search(String userId, String query, Pageable pageable) {
        Query filter = new Query(Criteria.where("userId").is(userId))
                .addCriteria(TextCriteria.forDefaultLanguage().matching(query));
        List<JournalEntry> content = mongo.find(Query.of(filter).with(pageable), JournalEntry.class);
        return PageableExecutionUtils.getPage(content, pageable, () -> mongo.count(filter, JournalEntry.class));
    }
}
