package com.ecommerce.test.kafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.ecommerce.common.kafka.KafkaTopics;

/**
 * Reads and writes raw records on the test broker, outside of the
 * application: to send what a broken producer would, or to check what landed
 * in a dead letter topic.
 */
public final class KafkaTestRecords {

    private KafkaTestRecords() {
    }

    /** Sends bytes as is, without the JSON serializer of the application. */
    public static void sendRaw(String topic, String key, byte[] value) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainer.bootstrapServers(),
                // An unknown topic fails in 10 s rather than the default 60 s.
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000);
        try (KafkaProducer<String, byte[]> producer =
                     new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot send to " + topic, e);
        }
    }

    /**
     * Waits for the record with this key in the dead letter topic of
     * {@code topic}. The DLT is shared by the tests: the key tells this test's
     * record apart.
     */
    public static ConsumerRecord<String, byte[]> awaitDeadLetter(String topic, String key, Duration timeout) {
        return awaitRecord(KafkaTopics.deadLetterTopicOf(topic), key, timeout);
    }

    /** Waits for the record with this key in {@code topic}, read from the start. */
    public static ConsumerRecord<String, byte[]> awaitRecord(String topic, String key, Duration timeout) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainer.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-reader-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, byte[]> consumer =
                     new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No record with key " + key + " in " + topic + " after " + timeout);
    }

    /** The value of a header, or {@code null} when the record does not have it. */
    public static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }
}
