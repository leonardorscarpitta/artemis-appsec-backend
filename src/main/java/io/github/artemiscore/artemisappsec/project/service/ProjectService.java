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

	List<ProjectSummary> findAll(UUID organizationId);

	/** Not scoped: for internal callers (scanner jobs) that already hold a trusted project id. */
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

	/** Deletes the repository and everything recorded for it (findings, runs, reports, keys, risk acceptances). */
	void delete(UUID organizationId, String name);

	List<PipelineStageView> defaultPipeline();

	void recordScan(UUID projectId, Instant finishedAt);

	/** How Artemis reaches the repository (deploy key, host key, last access check). */
	RepositoryAccess access(UUID organizationId, String name);

	/** Generates a new deploy key for a private repository; the previous one stops working. */
	RepositoryAccess rotateKey(UUID organizationId, String name);

	/** Queues an access check of a private repository (or returns the one already queued or running). */
	RepositoryAccess requestAccessCheck(UUID organizationId, String name);

	/** For the scanner: claims the oldest queued access check. */
	Optional<AccessCheckJob> claimAccessCheck();

	/** For the scanner: records a check result; {@code hostKey} (known_hosts lines) is kept on first contact only. */
	void completeAccessCheck(UUID checkId, boolean ok, String message, String hostKey);

	int failStaleAccessChecks(Duration maxDuration);

	/** For the scanner: the decrypted deploy key and recorded host key of a private repository. */
	Optional<SshCredentials> sshCredentials(UUID projectId, String purpose);

	/** Whether the scanner can verify the repository's SSH host (pinned provider or a recorded host key). */
	boolean hostKeyKnown(UUID projectId);

	/** Marks a private repository as needing a (new) deploy key, e.g. after its key was revoked. */
	void resetAccess(UUID projectId);
}
