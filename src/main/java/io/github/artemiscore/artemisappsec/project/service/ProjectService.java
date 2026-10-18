package io.github.artemiscore.artemisappsec.project.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public contract of the project (code repository) service. Other modules depend only on this interface.
 * Repositories belong to an organization; names are unique within it.
 */
public interface ProjectService {

	/** Connected repositories (removed ones are left out). */
	List<ProjectSummary> findAll(UUID organizationId);

	/** Connected and removed repositories: for showing the history (findings, runs) of removed ones by name. */
	List<ProjectSummary> findAllIncludingRemoved(UUID organizationId);

	/** Removed repositories whose history was kept. */
	List<RemovedRepository> findRemoved(UUID organizationId);

	/** Not scoped: for internal callers (scanner jobs) that already hold a trusted project id. Includes removed ones. */
	Optional<ProjectSummary> findById(UUID id);

	ProjectDetails getByName(UUID organizationId, String name);

	/** Connects a repository and publishes {@link ProjectCreated}. */
	ProjectDetails create(UUID organizationId, CreateProjectCommand command, String actor);

	ProjectDetails updateSettings(UUID organizationId, String name, ProjectSettings settings);

	ProjectDetails updatePipeline(UUID organizationId, String name, List<StageUpdate> stages);

	/**
	 * The pipeline a run executes: the repository's stages with the organization policy applied (required stages
	 * enabled, fail thresholds raised to the organization's minimum).
	 */
	List<PipelineStageView> effectivePipeline(UUID organizationId, String name);

	/**
	 * Removes the repository. With {@code purge} everything recorded for it is deleted too (findings, runs, reports,
	 * keys, risk acceptances); without it the history is kept, while the deploy key is destroyed, pending access checks
	 * are dropped and {@link ProjectRemoved} is published so its queued runs are cancelled.
	 */
	void delete(UUID organizationId, String name, boolean purge);

	/** Deletes a removed repository's kept history. */
	void purgeRemoved(UUID organizationId, UUID id);

	List<PipelineStageView> defaultPipeline();

	void recordScan(UUID projectId, Instant finishedAt);

	/**
	 * For the scheduler: claims up to {@code limit} repositories whose timed schedule is due and moves their next scan
	 * one interval ahead (so each due scan is handed out once, even with several API instances).
	 */
	List<DueScan> claimDueScans(int limit);

	/** How Artemis reaches the repository (deploy key, host key, last access check). */
	RepositoryAccess access(UUID organizationId, String name);

	/** Generates a new deploy key for a private repository; the previous one stops working. */
	RepositoryAccess rotateKey(UUID organizationId, String name);

	/** Queues an access check of a private repository (or returns the one already queued or running). */
	RepositoryAccess requestAccessCheck(UUID organizationId, String name);

	/** For the scanner: claims the oldest queued access check. */
	Optional<AccessCheckJob> claimAccessCheck();

	/** For the scanner: records a check result; {@code hostKey} (known_hosts lines) is kept on first contact only. */
	/** {@code detection}: the stack found in the repository's files on a successful check, or null. */
	void completeAccessCheck(UUID checkId, boolean ok, String message, String hostKey, StackDetection detection);

	int failStaleAccessChecks(Duration maxDuration);

	/** For the scanner: the decrypted deploy key and recorded host key of a private repository. */
	Optional<SshCredentials> sshCredentials(UUID projectId, String purpose);

	/** Whether the scanner can verify the repository's SSH host (pinned provider or a recorded host key). */
	boolean hostKeyKnown(UUID projectId);

	/** Marks a private repository as needing a (new) deploy key, e.g. after its key was revoked. */
	void resetAccess(UUID projectId);

	/** A scan found that the deploy key no longer works: records a failed access check and marks access as failed. */
	void recordAccessLost(UUID projectId, String message);
}
