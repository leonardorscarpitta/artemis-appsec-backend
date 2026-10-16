package io.github.artemiscore.artemis.project.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public contract of the project (code repository) service. Other modules depend only on this interface. */
public interface ProjectService {

	List<ProjectSummary> findAll();

	Optional<ProjectSummary> findById(UUID id);

	ProjectDetails getByName(String name);

	ProjectDetails create(CreateProjectCommand command);

	ProjectDetails updateSettings(String name, ProjectSettings settings);

	ProjectDetails updatePipeline(String name, List<StageUpdate> stages);

	List<PipelineStageView> defaultPipeline();
}
