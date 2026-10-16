package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;

/** Public contract of the pipeline run service. */
public interface PipelineRunService {

	List<RunView> find(UUID organizationId, RunFilter filter);

	RunsSummary summary(UUID organizationId);

	RunDetails get(UUID organizationId, long number);

	/** Queues a new manual run of the same commit using the repository's current pipeline configuration. */
	RunDetails rerun(UUID organizationId, long number, String actor);

	RunDetails cancel(UUID organizationId, long number);

	/** Queues a manual scan of a repository with its current pipeline configuration (counts against the plan's quota). */
	/** @param folders monorepo folders to scan; null or empty scans all of the repository's folders */
	RunDetails queueScan(UUID organizationId, String repository, String actor, List<String> folders);

	// --- Scanner runner (internal job API) ---

	/** Moves the oldest queued run to running and returns it, or empty when nothing is queued. */
	Optional<ClaimedRun> claimNext();

	RunJob job(UUID runId);

	void recordSource(UUID runId, String commitSha, String commitMessage);

	StageStatus recordStage(UUID runId, StageResult result);

	void complete(UUID runId);

	void fail(UUID runId, String reason);

	/** Fails runs that have been running for longer than {@code maxDuration}; returns how many. */
	int failStaleRuns(Duration maxDuration);
}
