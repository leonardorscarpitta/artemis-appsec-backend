package io.github.artemiscore.artemisappsec.scanjob.service;

import java.util.Optional;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** Coordinates the scanner runner: hands out queued runs and records what it reports. */
public interface ScanJobService {

	Optional<JobAssignment> claim();

	void recordSource(UUID runId, String commitSha, String commitMessage);

	/** Records a stage result and, when the stage completed, its findings; returns the final stage status. */
	StageStatus recordStage(UUID runId, Stage stage, StageReport report);

	void complete(UUID runId);

	void fail(UUID runId, String reason);
}
