package com.ecommerce.test.kafka;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Skips the Kafka integration tests on a machine without Docker, but never in
 * the CI: there, a missing or unusable Docker must fail the build. Otherwise a
 * broken environment would turn every integration test into a silent skip and
 * a green build that tested nothing.
 *
 * <p>GitHub Actions sets {@code CI=true}; set it locally to get the same
 * behaviour.
 */
public class DockerRequiredInCi implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            return ConditionEvaluationResult.enabled("CI: Docker is required, the test runs and fails without it");
        }
        if (DockerClientFactory.instance().isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        return ConditionEvaluationResult.disabled(
                "Docker is not available: Kafka integration tests skipped (set CI=true to make them fail instead)");
    }
}
