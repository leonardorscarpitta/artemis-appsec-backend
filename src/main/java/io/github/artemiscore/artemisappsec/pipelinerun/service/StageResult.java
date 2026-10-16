package io.github.artemiscore.artemisappsec.pipelinerun.service;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageOutcome;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/**
 * A stage result reported by the scanner runner, after its findings were recorded.
 * {@code highestSeverity} is null when there were no findings.
 */
public record StageResult(Stage stage, String tool, StageOutcome outcome, int durationSeconds, String log, int findings,
		Severity highestSeverity, int newFindings) {
}
