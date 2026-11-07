package com.ecommerce.test.kafka;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class of a Kafka integration test: the whole Spring Boot application,
 * connected to a real broker ({@link KafkaTestContainer}).
 *
 * <pre>{@code
 * class OrderPaidKafkaIntegrationTest extends KafkaIntegrationTest {
 *
 *     @Autowired
 *     KafkaTemplate<Object, Object> kafkaTemplate;
 *
 *     @Test
 *     void clearsTheCart() {
 *         kafkaTemplate.send("order.paid", "CMD-1", event);
 *         await().untilAsserted(() -> assertThat(...));
 *     }
 * }
 * }</pre>
 *
 * Without Docker, these tests are skipped instead of failing, so that the
 * build still runs on a machine without it, except in the CI, where they fail
 * (see {@link DockerRequiredInCi}).
 *
 * <p>The retry policy keeps its 3 retries but waits 100 ms instead of 1 s, so
 * that a test sending a record to the DLT stays fast.
 */
@SpringBootTest
@ExtendWith(DockerRequiredInCi.class)
public abstract class KafkaIntegrationTest {

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainer::bootstrapServers);
        registry.add("app.kafka.retry.initial-interval", () -> "100ms");
        registry.add("app.kafka.retry.max-interval", () -> "400ms");
    }
}
