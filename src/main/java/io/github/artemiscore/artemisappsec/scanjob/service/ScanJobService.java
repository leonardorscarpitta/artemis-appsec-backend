package io.github.artemiscore.artemisappsec.scanjob.service;

import java.util.Optional;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** Coordinates the scanner runner: hands out queued runs and records what it reports. */
public interface ScanJobService {

	/** The next access check, else the next queued run; empty when there is nothing to do. */
	Optional<Assignment> claim();

	/** Records an access check result; {@code hostKey} is the server's known_hosts line(s) seen by the runner. */
	void completeAccessCheck(UUID checkId, boolean ok, String message, String hostKey);

	void recordSource(UUID runId, String commitSha, String commitMessage);

	/** Records a stage result and, when the stage completed, its findings; returns the final stage status. */
	StageStatus recordStage(UUID runId, Stage stage, StageReport report);

	void complete(UUID runId);

	void fail(UUID runId, String reason);
}
