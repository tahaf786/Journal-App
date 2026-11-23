package com.devtaha.journalapp.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.listener.BatchListenerFailedException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntryEventConsumerTest {

    private final StatsService stats = mock(StatsService.class);
    private final EntryEventConsumer consumer = new EntryEventConsumer(stats);

    private static EntryEvent event(String id) {
        return new EntryEvent(id, EntryEventType.CREATED, "e", "u1", Instant.now(), Instant.now());
    }

    private static ConsumerRecord<String, EntryEvent> record(long offset, EntryEvent value) {
        return new ConsumerRecord<>("topic", 0, offset, "u1", value);
    }

    @Test
    void aWholePollIsAppliedInOneCall() {
        EntryEvent a = event("a");
        EntryEvent b = event("b");

        consumer.onEvents(List.of(record(0, a), record(1, b)));

        verify(stats).applyBatch(List.of(a, b));
    }

    @Test
    void aFailedBatchIsRetriedOneByOneAndReportsTheFailingRecord() {
        EntryEvent a = event("a");
        EntryEvent b = event("b");
        EntryEvent c = event("c");
        when(stats.applyBatch(List.of(a, b, c))).thenThrow(new DataAccessResourceFailureException("down"));
        when(stats.applyBatch(List.of(b))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> consumer.onEvents(List.of(record(0, a), record(1, b), record(2, c))))
                .isInstanceOfSatisfying(BatchListenerFailedException.class,
                        e -> assertThat(e.getIndex()).isEqualTo(1));

        verify(stats).applyBatch(List.of(a));
        verify(stats, never()).applyBatch(List.of(c));
    }

    @Test
    void recordThatCouldNotBeDeserializedIsHandedToTheErrorHandlerAfterTheGoodOnes() {
        EntryEvent a = event("a");
        EntryEvent c = event("c");

        assertThatThrownBy(() -> consumer.onEvents(List.of(record(0, a), record(1, null), record(2, c))))
                .isInstanceOfSatisfying(BatchListenerFailedException.class,
                        e -> assertThat(e.getIndex()).isEqualTo(1));

        verify(stats).applyBatch(List.of(a));
        verify(stats, never()).applyBatch(List.of(c));
    }
}
