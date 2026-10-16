package io.github.artemiscore.artemisappsec.finding.service;

import io.github.artemiscore.artemisappsec.shared.domain.Severity;

/**
 * Outcome of recording one stage's detections. {@code highestSeverity} is null when nothing was reported.
 */
public record IngestionResult(int reported, int newFindings, int reopened, int fixed, Severity highestSeverity) {
}
