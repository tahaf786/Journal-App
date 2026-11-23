package com.devtaha.journalapp.config;

import com.devtaha.journalapp.event.EntryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class KafkaConfig {

    /**
     * Events go out as JSON. Raw bytes are also accepted so that a message that
     * could not be deserialized can be forwarded unchanged to the dead-letter topic.
     */
    @Bean
    ProducerFactory<String, Object> producerFactory(KafkaProperties properties, ObjectMapper mapper) {
        JsonSerializer<Object> json = new JsonSerializer<>(mapper);
        json.setAddTypeInfo(false);
        DelegatingByTypeSerializer value = new DelegatingByTypeSerializer(
                Map.of(byte[].class, new ByteArraySerializer(), EntryEvent.class, json));
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null),
                new StringSerializer(), value);
    }

    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    NewTopic entryEventsTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().entryEventsTopic()).partitions(props.kafka().partitions())
                .replicas(1).build();
    }

    @Bean
    NewTopic deadLetterTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().deadLetterTopic()).partitions(props.kafka().partitions())
                .replicas(1).build();
    }

    /**
     * Batch listener container for the stats consumer: one thread per partition, each handling a whole poll.
     * The partition count and the concurrency come from the same property.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, EntryEvent> statsBatchListenerFactory(
            ConsumerFactory<String, EntryEvent> consumerFactory, CommonErrorHandler kafkaErrorHandler,
            AppProperties props) {
        ConcurrentKafkaListenerContainerFactory<String, EntryEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(props.kafka().partitions());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    /** Two quick retries, then the record goes to the dead-letter topic on the same partition number. */
    @Bean
    CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template, AppProperties props) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(props.kafka().deadLetterTopic(), record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
    }
}
