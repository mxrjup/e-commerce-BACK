package com.ecommerce.common.kafka;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Retry and dead letter policy shared by every {@code @KafkaListener}.
 *
 * <p>A listener that throws is retried in place, with an exponential back-off
 * (see {@link KafkaRetryProperties}). Once the retries are exhausted, the
 * record is published to {@code <topic>.dlt} with the exception in its
 * headers, and the consumer moves on: one bad record never blocks a partition.
 * Records that can never succeed (a body that is not valid JSON, an unknown
 * event type) skip the retries and go to the DLT at once.
 *
 * <p>Spring Boot hands the {@link CommonErrorHandler} bean to the listener
 * container factory: a service has nothing to declare.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(ProducerFactory.class)
@EnableConfigurationProperties(KafkaRetryProperties.class)
public class KafkaErrorHandlingAutoConfiguration {

    /**
     * Publishes to the dead letter topic. Two templates: a record that failed
     * deserialization only has its original bytes, which are copied as is; any
     * other record is written back as JSON, like the original.
     */
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterPublishingRecoverer kafkaDeadLetterPublishingRecoverer(
            KafkaTemplate<?, ?> kafkaTemplate, ProducerFactory<?, ?> producerFactory) {
        KafkaTemplate<?, ?> rawBytesTemplate = new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));

        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, rawBytesTemplate);
        templates.put(Object.class, kafkaTemplate);

        // No partition (-1): the producer picks it from the key, so the DLT
        // does not need as many partitions as the original topic.
        return new DeadLetterPublishingRecoverer(templates,
                (record, exception) -> new TopicPartition(KafkaTopics.deadLetterTopicOf(record.topic()), -1));
    }

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer,
                                                 KafkaRetryProperties retry) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.getMaxRetries());
        backOff.setInitialInterval(retry.getInitialInterval().toMillis());
        backOff.setMultiplier(retry.getMultiplier());
        backOff.setMaxInterval(retry.getMaxInterval().toMillis());
        // DefaultErrorHandler already treats deserialization and conversion
        // failures as not retryable: they go to the DLT without waiting.
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
