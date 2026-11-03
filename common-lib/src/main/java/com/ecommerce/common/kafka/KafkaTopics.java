package com.ecommerce.common.kafka;

/**
 * Default topic names. They are the contract between a producer and its
 * consumers, and must match {@code docker/kafka/create-topics.sh} and the
 * topics Job of e-commerce-INFRA: the broker does not create topics on demand.
 *
 * <p>Services do not use these constants directly: they read the topic from
 * the {@code app.kafka.topics.*} property, which defaults to these values and
 * can be overridden per environment.
 */
public final class KafkaTopics {

    /** Property: {@code app.kafka.topics.order-paid}. */
    public static final String ORDER_PAID = "order.paid";

    /** Property: {@code app.kafka.topics.catalog-events}. */
    public static final String CATALOG_EVENTS = "catalogue.evenements";

    /** Property: {@code app.kafka.topics.support-admin-reply}. */
    public static final String SUPPORT_ADMIN_REPLY = "sav.reponse-admin";

    /**
     * Appended to a topic name to get its dead letter topic, where a record
     * lands once its retries are exhausted: {@code order.paid.dlt}.
     */
    public static final String DLT_SUFFIX = ".dlt";

    private KafkaTopics() {
    }

    public static String deadLetterTopicOf(String topic) {
        return topic + DLT_SUFFIX;
    }
}
