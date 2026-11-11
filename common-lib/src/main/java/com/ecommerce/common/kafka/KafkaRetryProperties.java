package com.ecommerce.common.kafka;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Retry policy of the Kafka listeners, under {@code app.kafka.retry}.
 *
 * <p>With the defaults, a failing record is tried 4 times in about 7 seconds
 * (1 s, 2 s, 4 s between attempts) before going to its dead letter topic.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka.retry")
public class KafkaRetryProperties {

    /** Retries after the first attempt. 0 sends a failure to the DLT at once. */
    private int maxRetries = 3;

    /** Wait before the first retry. */
    private Duration initialInterval = Duration.ofSeconds(1);

    /** Factor applied to the wait after each retry. */
    private double multiplier = 2.0;

    /** Upper bound of the wait between two retries. */
    private Duration maxInterval = Duration.ofSeconds(10);
}
