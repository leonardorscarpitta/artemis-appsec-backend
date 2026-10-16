package io.github.artemiscore.artemis.pipelinerun.service;

import java.util.List;

import io.github.artemiscore.artemis.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemis.shared.domain.Phase;
import io.github.artemiscore.artemis.shared.domain.Severity;
import io.github.artemiscore.artemis.shared.domain.Stage;

/** {@code failedBy} is the severity threshold that failed the stage, or null. */
public record StageRunView(Stage id, Phase phase, String tool, StageStatus status, int durationSeconds, int findings,
		Severity failedBy, List<String> log) {
}
