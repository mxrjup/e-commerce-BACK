package com.ecommerce.common.kafka;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultErrorHandler waits for each interval of the back-off before a retry,
 * and sends the record to the DLT once it returns STOP: the intervals listed
 * here are exactly the retries a failing record gets.
 */
class KafkaRetryBackOffTest {

    @Test
    void shouldWaitOneTwoThenFourSecondsByDefault() {
        assertThat(intervals(new KafkaRetryProperties())).containsExactly(1000L, 2000L, 4000L);
    }

    @Test
    void shouldCapTheWaitAtTheMaxInterval() {
        KafkaRetryProperties retry = new KafkaRetryProperties();
        retry.setMaxRetries(5);
        retry.setMaxInterval(Duration.ofSeconds(3));

        assertThat(intervals(retry)).containsExactly(1000L, 2000L, 3000L, 3000L, 3000L);
    }

    @Test
    void shouldNotRetryAtAllWhenMaxRetriesIsZero() {
        KafkaRetryProperties retry = new KafkaRetryProperties();
        retry.setMaxRetries(0);

        assertThat(intervals(retry)).isEmpty();
    }

    private static List<Long> intervals(KafkaRetryProperties retry) {
        BackOff backOff = KafkaErrorHandlingAutoConfiguration.backOff(retry);
        BackOffExecution execution = backOff.start();
        List<Long> intervals = new ArrayList<>();
        for (long next = execution.nextBackOff(); next != BackOffExecution.STOP; next = execution.nextBackOff()) {
            intervals.add(next);
            if (intervals.size() > 20) {
                break;
            }
        }
        return intervals;
    }
}
