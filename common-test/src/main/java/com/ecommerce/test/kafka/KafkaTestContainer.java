package com.ecommerce.test.kafka;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One Kafka broker for all the integration tests of a JVM, configured like the
 * local stack and the cluster: same image, topic auto-creation disabled, and
 * the topics created by the real {@code docker/kafka/create-topics.sh}.
 *
 * <p>Running the script rather than a copy of its list keeps a single source
 * of truth for the topic names, and tests the script on every build. A topic
 * missing from the script makes the test fail like it would in production.
 *
 * <p>The broker starts on first use and stops with the JVM (Testcontainers'
 * Ryuk container removes it). Topics and consumer group offsets are shared by
 * every test class: use unique keys and identifiers in each test.
 */
public final class KafkaTestContainer {

    /** Same image as compose.yaml and the StatefulSet of e-commerce-INFRA. */
    public static final String IMAGE = "apache/kafka:3.8.1";

    static final String TOPICS_SCRIPT = "docker/kafka/create-topics.sh";

    /**
     * Listener of the Testcontainers image reachable from inside the
     * container; the PLAINTEXT one advertises the port mapped on the host.
     */
    private static final String INTERNAL_BOOTSTRAP = "localhost:9093";

    private static KafkaContainer container;

    private KafkaTestContainer() {
    }

    /** Starts the broker and creates the topics on the first call. */
    public static synchronized String bootstrapServers() {
        if (container == null) {
            KafkaContainer kafka = new KafkaContainer(DockerImageName.parse(IMAGE))
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false")
                    .withCopyFileToContainer(MountableFile.forHostPath(topicsScript()), "/create-topics.sh");
            kafka.start();
            createTopics(kafka);
            container = kafka;
        }
        return container.getBootstrapServers();
    }

    private static void createTopics(KafkaContainer kafka) {
        try {
            ExecResult result = kafka.execInContainer("bash", "-c",
                    "KAFKA_BOOTSTRAP_SERVER=" + INTERNAL_BOOTSTRAP + " bash /create-topics.sh");
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(TOPICS_SCRIPT + " failed (exit " + result.getExitCode() + "):\n"
                        + result.getStdout() + result.getStderr());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating the topics", e);
        }
    }

    /** The script lives at the root of the repository; tests run in a module. */
    static Path topicsScript() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path script = dir.resolve(TOPICS_SCRIPT);
            if (Files.isRegularFile(script)) {
                return script;
            }
        }
        throw new IllegalStateException(TOPICS_SCRIPT + " not found above " + Path.of("").toAbsolutePath());
    }
}
