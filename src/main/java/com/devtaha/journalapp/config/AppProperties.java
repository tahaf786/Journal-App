package com.devtaha.journalapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cache cache, Kafka kafka, Admin admin) {

    public record Jwt(String secret,
                      @DefaultValue("15m") Duration accessTtl,
                      @DefaultValue("7d") Duration refreshTtl) {
    }

    public record Cache(@DefaultValue("true") boolean enabled,
                        @DefaultValue("10m") Duration entryTtl,
                        @DefaultValue("2m") Duration pageTtl) {
    }

    public record Kafka(String entryEventsTopic, String deadLetterTopic, @DefaultValue("3") int partitions) {
    }

    public record Admin(@DefaultValue("") String username, @DefaultValue("") String password) {
    }
}
