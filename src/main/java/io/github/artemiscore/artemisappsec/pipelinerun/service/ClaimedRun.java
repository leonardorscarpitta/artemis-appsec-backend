package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.util.List;
import java.util.UUID;

/** A run handed to the scanner runner; {@code stages} are the ones it must execute, in order. */
public record ClaimedRun(UUID runId, long number, UUID projectId, String branch, List<StageAssignment> stages,
		List<String> folders) {
}
