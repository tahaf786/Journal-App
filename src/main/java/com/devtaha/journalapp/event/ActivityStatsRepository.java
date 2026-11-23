package com.devtaha.journalapp.event;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityStatsRepository extends MongoRepository<ActivityStats, String> {
}
