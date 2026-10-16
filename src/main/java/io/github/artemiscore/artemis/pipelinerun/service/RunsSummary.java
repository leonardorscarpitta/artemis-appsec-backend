package io.github.artemiscore.artemis.pipelinerun.service;

/** Aggregates over the last 7 days. {@code successRate} is a percentage of finished runs. */
public record RunsSummary(int runs, int successRate, int averageDurationSeconds, int failedByPolicy) {
}
