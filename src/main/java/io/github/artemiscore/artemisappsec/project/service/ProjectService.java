package io.github.artemiscore.artemisappsec.project.service;

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

	List<PipelineStageView> defaultPipeline();

	void recordScan(UUID projectId, Instant finishedAt);
}
