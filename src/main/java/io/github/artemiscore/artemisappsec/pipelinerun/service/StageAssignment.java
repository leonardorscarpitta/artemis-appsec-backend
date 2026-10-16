package io.github.artemiscore.artemisappsec.pipelinerun.service;

import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** {@code failOn} is null for stages that never fail the pipeline. */
public record StageAssignment(Stage id, String tool, FailThreshold failOn) {
}
