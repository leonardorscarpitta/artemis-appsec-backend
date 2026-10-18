package io.github.artemiscore.artemisappsec.pipelinerun.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;

/** Public contract of the pipeline run service. */
public interface PipelineRunService {

	List<RunView> find(UUID organizationId, RunFilter filter);

	RunsSummary summary(UUID organizationId);

	/** Runs per day since {@code from} (inclusive), with days in {@code zone}; days without runs are left out. */
	List<RunActivity> activity(UUID organizationId, LocalDate from, ZoneId zone);

	RunDetails get(UUID organizationId, long number);

	/** Queues a new manual run of the same commit using the repository's current pipeline configuration. */
	RunDetails rerun(UUID organizationId, long number, String actor);

	RunDetails cancel(UUID organizationId, long number);

	/** Cancels the queued and running runs of a repository (it was removed); returns how many. */
	int cancelActive(UUID projectId);

	/** Queues a manual scan of a repository with its current pipeline configuration (counts against the monthly quota). */
	/** @param folders monorepo folders to scan; null or empty scans all of the repository's folders */
	RunDetails queueScan(UUID organizationId, String repository, String actor, List<String> folders);

	/**
	 * Queues a scheduled run (counts against the quota like a manual scan). Empty when it is skipped: scheduled scans are not
	 * enabled, access is not verified, or a run of the repository is already queued or running.
	 */
	Optional<RunDetails> queueScheduledScan(UUID organizationId, String repository);

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
