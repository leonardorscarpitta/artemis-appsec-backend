package io.github.artemiscore.artemis.project.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemis.project.domain.PipelineStageConfig;
import io.github.artemiscore.artemis.project.domain.Project;
import io.github.artemiscore.artemis.project.persistence.ProjectRepository;
import io.github.artemiscore.artemis.shared.domain.Stage;
import io.github.artemiscore.artemis.shared.error.BusinessRuleException;
import io.github.artemiscore.artemis.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class ProjectServiceImpl implements ProjectService {

	private final ProjectRepository projects;

	ProjectServiceImpl(ProjectRepository projects) {
		this.projects = projects;
	}

	@Override
	public List<ProjectSummary> findAll() {
		return projects.findAllByOrderByNameAsc().stream().map(ProjectServiceImpl::toSummary).toList();
	}

	@Override
	public Optional<ProjectSummary> findById(UUID id) {
		return projects.findById(id).map(ProjectServiceImpl::toSummary);
	}

	@Override
	public ProjectDetails getByName(String name) {
		return toDetails(load(name));
	}

	@Override
	@Transactional
	public ProjectDetails create(CreateProjectCommand command) {
		String name = nameFromUrl(command.url());
		if (projects.existsByName(name)) {
			throw new BusinessRuleException("A repository named '%s' is already connected".formatted(name));
		}
		Project project = new Project(name, command.url(), command.branch(), command.stack());
		applySettings(project, command.settings());
		applyPipeline(project, command.pipeline());
		return toDetails(projects.save(project));
	}

	@Override
	@Transactional
	public ProjectDetails updateSettings(String name, ProjectSettings settings) {
		Project project = load(name);
		applySettings(project, settings);
		return toDetails(project);
	}

	@Override
	@Transactional
	public ProjectDetails updatePipeline(String name, List<StageUpdate> stages) {
		Project project = load(name);
		applyPipeline(project, stages);
		return toDetails(project);
	}

	@Override
	public List<PipelineStageView> defaultPipeline() {
		return Arrays.stream(Stage.values()).map(PipelineStageView::defaults).toList();
	}

	private Project load(String name) {
		return projects.findByName(name).orElseThrow(() -> new ResourceNotFoundException("Repository", name));
	}

	private static void applySettings(Project project, ProjectSettings settings) {
		project.updateSettings(settings.schedule(), settings.automaticActions(), settings.dockerfileMode(),
				settings.customDockerfile());
	}

	private static void applyPipeline(Project project, List<StageUpdate> stages) {
		stages.forEach(s -> project.configureStage(s.id(), s.enabled(), s.tool(), s.failOn()));
	}

	static String nameFromUrl(String url) {
		String path = url.substring(Math.max(url.lastIndexOf('/'), url.lastIndexOf(':')) + 1);
		return path.endsWith(".git") ? path.substring(0, path.length() - 4) : path;
	}

	private static ProjectSummary toSummary(Project p) {
		return new ProjectSummary(p.getId(), p.getName(), p.getSshUrl(), p.getBranch(), p.getStack(), p.getScanSchedule(),
				p.getLastScanAt());
	}

	private static ProjectDetails toDetails(Project p) {
		ProjectSettings settings = new ProjectSettings(p.getScanSchedule(), p.getAutomaticActions(), p.getDockerfileMode(),
				p.getCustomDockerfile());
		return new ProjectDetails(toSummary(p), settings, p.getPipeline().stream().map(ProjectServiceImpl::toView).toList());
	}

	private static PipelineStageView toView(PipelineStageConfig c) {
		Stage s = c.getStage();
		return new PipelineStageView(s, s.phase(), s.tools(), c.getTool(), c.isEnabled(), c.getFailOn(), s.estimatedMinutes());
	}
}
