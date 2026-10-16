package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.util.List;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Phase;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** {@code failedBy} is the severity threshold that failed the stage, or null. */
public record StageRunView(Stage id, Phase phase, String tool, StageStatus status, int durationSeconds, int findings,
		Severity failedBy, List<String> log) {
}
