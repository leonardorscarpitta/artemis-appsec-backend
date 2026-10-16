package io.github.artemiscore.artemisappsec.project.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.project.domain.PipelineStageConfig;
import io.github.artemiscore.artemisappsec.project.domain.Project;
import io.github.artemiscore.artemisappsec.project.persistence.ProjectRepository;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class ProjectServiceImpl implements ProjectService {

	private final ProjectRepository projects;
	private final ObjectProvider<ProjectFindingStats> findingStats;
	private final ApplicationEventPublisher events;

	ProjectServiceImpl(ProjectRepository projects, ObjectProvider<ProjectFindingStats> findingStats,
			ApplicationEventPublisher events) {
		this.projects = projects;
		this.findingStats = findingStats;
		this.events = events;
	}

	@Override
	public List<ProjectSummary> findAll(UUID organizationId) {
		List<Project> found = projects.findByOrganizationIdOrderByNameAsc(organizationId);
		Map<UUID, Long> open = openFindings(found);
		return found.stream().map(p -> toSummary(p, open)).toList();
	}

	@Override
	public Optional<ProjectSummary> findById(UUID id) {
		return projects.findById(id).map(p -> toSummary(p, openFindings(List.of(p))));
	}

	@Override
	public ProjectDetails getByName(UUID organizationId, String name) {
		return toDetails(load(organizationId, name));
	}

	@Override
	@Transactional
	public ProjectDetails create(UUID organizationId, CreateProjectCommand command, String actor) {
		String name = nameFromUrl(command.url());
		if (projects.existsByOrganizationIdAndName(organizationId, name)) {
			throw new BusinessRuleException("A repository named '%s' is already connected".formatted(name));
		}
		Project project = new Project(organizationId, name, command.url(), command.branch(), command.stack());
		applySettings(project, command.settings());
		applyPipeline(project, command.pipeline());
		Project saved = projects.save(project);
		events.publishEvent(new ProjectCreated(saved.getId(), organizationId, saved.getName(), actor));
		return toDetails(saved);
	}

	@Override
	@Transactional
	public void recordScan(UUID projectId, Instant finishedAt) {
		projects.findById(projectId).ifPresent(p -> p.recordScan(finishedAt));
	}

	@Override
	@Transactional
	public ProjectDetails updateSettings(UUID organizationId, String name, ProjectSettings settings) {
		Project project = load(organizationId, name);
		applySettings(project, settings);
		return toDetails(project);
	}

	@Override
	@Transactional
	public ProjectDetails updatePipeline(UUID organizationId, String name, List<StageUpdate> stages) {
		Project project = load(organizationId, name);
		applyPipeline(project, stages);
		return toDetails(project);
	}

	@Override
	public List<PipelineStageView> defaultPipeline() {
		return Arrays.stream(Stage.values()).map(PipelineStageView::defaults).toList();
	}

	private Project load(UUID organizationId, String name) {
		return projects.findByOrganizationIdAndName(organizationId, name).orElseThrow(() -> new ResourceNotFoundException("Repository", name));
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

	private Map<UUID, Long> openFindings(List<Project> scope) {
		ProjectFindingStats stats = findingStats.getIfAvailable();
		return stats == null ? null : stats.openFindingsByProject(scope.stream().map(Project::getId).toList());
	}

	private static ProjectSummary toSummary(Project p, Map<UUID, Long> openFindings) {
		Long open = openFindings == null ? null : openFindings.getOrDefault(p.getId(), 0L);
		return new ProjectSummary(p.getId(), p.getOrganizationId(), p.getName(), p.getSshUrl(), p.getBranch(), p.getStack(), p.getScanSchedule(),
				p.getLastScanAt(), open);
	}

	private ProjectDetails toDetails(Project p) {
		ProjectSettings settings = new ProjectSettings(p.getScanSchedule(), p.getAutomaticActions(), p.getDockerfileMode(),
				p.getCustomDockerfile());
		return new ProjectDetails(toSummary(p, openFindings(List.of(p))), settings, p.getPipeline().stream().map(ProjectServiceImpl::toView).toList());
	}

	private static PipelineStageView toView(PipelineStageConfig c) {
		Stage s = c.getStage();
		return new PipelineStageView(s, s.phase(), s.tools(), c.getTool(), c.isEnabled(), c.getFailOn(), s.estimatedMinutes(),
				s.available());
	}
}
