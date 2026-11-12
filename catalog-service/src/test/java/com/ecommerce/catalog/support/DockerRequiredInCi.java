package com.ecommerce.catalog.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Skips the PostgreSQL tests on a machine without Docker, never in the CI
 * ({@code CI=true}), where a missing Docker must fail the build instead of
 * turning the test into a silent skip. Same rule as the Kafka tests of
 * {@code common-test}, kept here to spare the catalogue its Kafka
 * dependencies.
 */
public class DockerRequiredInCi implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            return ConditionEvaluationResult.enabled("CI: Docker is required");
        }
        if (DockerClientFactory.instance().isDockerAvailable()) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        return ConditionEvaluationResult.disabled("Docker is not available: PostgreSQL tests skipped");
    }
}
