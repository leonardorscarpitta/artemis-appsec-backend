package io.github.artemiscore.artemisappsec.project.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.project.domain.AccessCheck;
import io.github.artemiscore.artemisappsec.project.domain.AccessCheckStatus;
import io.github.artemiscore.artemisappsec.project.domain.AccessStatus;
import io.github.artemiscore.artemisappsec.project.domain.Folder;
import io.github.artemiscore.artemisappsec.project.domain.PipelineStageConfig;
import io.github.artemiscore.artemisappsec.project.domain.Project;
import io.github.artemiscore.artemisappsec.project.persistence.AccessCheckRepository;
import io.github.artemiscore.artemisappsec.project.persistence.ProjectRepository;
import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class ProjectServiceImpl implements ProjectService {

	private static final List<AccessCheckStatus> ACTIVE_CHECKS = List.of(AccessCheckStatus.QUEUED, AccessCheckStatus.RUNNING);

	private final ProjectRepository projects;
	private final ObjectProvider<ProjectFindingStats> findingStats;
	private final ApplicationEventPublisher events;
	private final AccessCheckRepository checks;
	private final RepositoryKeys keys;
	private final ObjectProvider<PipelinePolicy> pipelinePolicy;

	ProjectServiceImpl(ProjectRepository projects, ObjectProvider<ProjectFindingStats> findingStats,
			ApplicationEventPublisher events, AccessCheckRepository checks, RepositoryKeys keys,
			ObjectProvider<PipelinePolicy> pipelinePolicy) {
		this.pipelinePolicy = pipelinePolicy;
		this.projects = projects;
		this.findingStats = findingStats;
		this.events = events;
		this.checks = checks;
		this.keys = keys;
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
		// Every repository is cloned over SSH with its own deploy key, which also proves the user controls it.
		if (!Project.isPrivateUrl(command.url()) || GitHost.of(command.url()).isEmpty()) {
			throw new IllegalArgumentException("Use an SSH URL such as git@github.com:owner/repo.git or ssh://git@host/owner/repo.git");
		}
		if (!keys.enabled()) {
			throw deployKeysDisabled();
		}
		Project project = new Project(organizationId, name, command.url(), command.branch(), command.stack());
		applySettings(project, command.settings());
		applyPipeline(project, command.pipeline());
		Project saved = projects.save(project);
		keys.generate(saved.getId(), saved.getName());
		events.publishEvent(new ProjectCreated(saved.getId(), organizationId, saved.getName(), actor, true));
		return toDetails(saved);
	}

	@Override
	@Transactional
	public void recordScan(UUID projectId, Instant finishedAt) {
		projects.findById(projectId).ifPresent(p -> p.recordScan(finishedAt));
	}

	@Override
	public RepositoryAccess access(UUID organizationId, String name) {
		return toAccess(load(organizationId, name));
	}

	@Override
	@Transactional
	public RepositoryAccess rotateKey(UUID organizationId, String name) {
		Project project = loadPrivate(organizationId, name);
		if (!keys.enabled()) {
			throw deployKeysDisabled();
		}
		keys.generate(project.getId(), project.getName());
		project.updateAccess(AccessStatus.PENDING);
		return toAccess(project);
	}

	@Override
	@Transactional
	public RepositoryAccess requestAccessCheck(UUID organizationId, String name) {
		Project project = loadPrivate(organizationId, name);
		if (keys.find(project.getId()).isEmpty()) {
			throw new BusinessRuleException("'%s' has no deploy key; generate one first".formatted(project.getName()));
		}
		if (checks.findFirstByProjectIdAndStatusIn(project.getId(), ACTIVE_CHECKS).isEmpty()) {
			checks.save(new AccessCheck(project.getId()));
		}
		return toAccess(project);
	}

	@Override
	@Transactional
	public Optional<AccessCheckJob> claimAccessCheck() {
		return checks.lockNextQueued().map(check -> {
			check.start();
			Project project = projects.findById(check.getProjectId()).orElseThrow();
			return new AccessCheckJob(check.getId(), project.getId(), project.getName(), project.getSshUrl(),
					project.getBranch());
		});
	}

	@Override
	@Transactional
	public void completeAccessCheck(UUID checkId, boolean ok, String message, String hostKey) {
		AccessCheck check = checks.findById(checkId).orElseThrow(() -> new ResourceNotFoundException("Access check", checkId));
		if (!check.isActive()) {
			throw new BusinessRuleException("Access check %s is already finished".formatted(checkId));
		}
		check.finish(ok, message);
		projects.findById(check.getProjectId()).ifPresent(project -> {
			project.updateAccess(ok ? AccessStatus.VERIFIED : AccessStatus.FAILED);
			GitHost host = GitHost.of(project.getSshUrl()).orElse(null);
			if (ok && host != null && !host.pinned()) {
				host.sanitizeKnownHosts(hostKey).ifPresent(project::recordHostKeyIfAbsent);
			}
		});
	}

	@Override
	@Transactional
	public int failStaleAccessChecks(Duration maxDuration) {
		List<AccessCheck> stale = checks.findByStatusAndStartedAtBefore(AccessCheckStatus.RUNNING,
				Instant.now().minus(maxDuration));
		stale.forEach(check -> {
			check.finish(false, "The scanner did not finish the access check in time.");
			projects.findById(check.getProjectId()).ifPresent(p -> p.updateAccess(AccessStatus.FAILED));
		});
		return stale.size();
	}

	@Override
	@Transactional
	public Optional<SshCredentials> sshCredentials(UUID projectId, String purpose) {
		return projects.findById(projectId)
			.filter(Project::isPrivate)
			.flatMap(project -> keys.releasePrivateKey(projectId, purpose)
				.map(privateKey -> new SshCredentials(privateKey, project.getSshKnownHost())));
	}

	@Override
	public boolean hostKeyKnown(UUID projectId) {
		return projects.findById(projectId)
			.map(p -> p.getSshKnownHost() != null || GitHost.of(p.getSshUrl()).map(GitHost::pinned).orElse(false))
			.orElse(false);
	}

	@Override
	@Transactional
	public void resetAccess(UUID projectId) {
		projects.findById(projectId).ifPresent(p -> p.updateAccess(AccessStatus.PENDING));
	}

	private Project loadPrivate(UUID organizationId, String name) {
		Project project = load(organizationId, name);
		if (!project.isPrivate()) {
			throw new BusinessRuleException("'%s' is a public repository; it needs no deploy key".formatted(name));
		}
		return project;
	}

	private RepositoryAccess toAccess(Project project) {
		if (!project.isPrivate()) {
			return RepositoryAccess.publicRepository();
		}
		GitHost host = GitHost.of(project.getSshUrl()).orElse(null);
		boolean pinned = host != null && host.pinned();
		String fingerprint = !pinned && project.getSshKnownHost() != null ? GitHost.fingerprint(project.getSshKnownHost()) : null;
		AccessCheckView lastCheck = checks.findFirstByProjectIdOrderByRequestedAtDesc(project.getId())
			.map(c -> new AccessCheckView(c.getId(), c.getStatus(), c.getMessage(), c.getRequestedAt(), c.getFinishedAt()))
			.orElse(null);
		return new RepositoryAccess("ssh", keys.find(project.getId()).orElse(null), host == null ? null : host.host(),
				fingerprint, pinned, lastCheck);
	}

	private static ApiProblemException deployKeysDisabled() {
		return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "deploy-keys-disabled",
				"Private repositories are not configured on this server");
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
	public List<PipelineStageView> effectivePipeline(UUID organizationId, String name) {
		Project project = load(organizationId, name);
		Map<Stage, PipelinePolicy.Gate> gates = gates(organizationId);
		return project.getPipeline().stream().map(c -> {
			PipelineStageView view = toView(c);
			PipelinePolicy.Gate gate = gates.get(c.getStage());
			if (gate == null) {
				return view;
			}
			boolean enabled = view.enabled() || (gate.required() && view.available());
			FailThreshold failOn = view.id().canFail() ? gate.strictest(view.failOn()) : view.failOn();
			return new PipelineStageView(view.id(), view.phase(), view.tools(), view.tool(), enabled, failOn, view.minutes(),
					view.available());
		}).toList();
	}

	private Map<Stage, PipelinePolicy.Gate> gates(UUID organizationId) {
		PipelinePolicy policy = pipelinePolicy.getIfAvailable();
		return policy == null ? Map.of() : policy.gates(organizationId);
	}

	@Override
	@Transactional
	public void delete(UUID organizationId, String name) {
		projects.delete(load(organizationId, name));
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
		long distinct = settings.folders().stream().map(ProjectFolder::path).distinct().count();
		if (distinct != settings.folders().size()) {
			throw new BusinessRuleException("Each monorepo folder can be listed only once");
		}
		project.updateFolders(settings.folders().stream().map(f -> new Folder(f.path(), f.stack())).toList());
		project.updateContainerImage(settings.containerImage());
	}

	private void applyPipeline(Project project, List<StageUpdate> stages) {
		Map<Stage, PipelinePolicy.Gate> gates = gates(project.getOrganizationId());
		for (StageUpdate s : stages) {
			PipelinePolicy.Gate gate = gates.get(s.id());
			if (gate == null) {
				continue;
			}
			if (gate.required() && s.id().available() && !s.enabled()) {
				throw new BusinessRuleException("The %s stage is required by the organization policy and cannot be disabled"
					.formatted(s.id().wireValue()));
			}
			if (s.enabled() && s.id().canFail() && !gate.allows(s.failOn())) {
				throw new BusinessRuleException("The organization policy requires the %s stage to fail on %s or stricter"
					.formatted(s.id().wireValue(), gate.minimumFailOn().wireValue()));
			}
		}
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
		return new ProjectSummary(p.getId(), p.getOrganizationId(), p.getName(), p.getSshUrl(), p.getBranch(), p.getStack(), folders(p), p.getContainerImage(), p.isPrivate() ? "ssh" : "public", p.getAccessStatus(), p.getScanSchedule(),
				p.getLastScanAt(), open);
	}

	private ProjectDetails toDetails(Project p) {
		ProjectSettings settings = new ProjectSettings(p.getScanSchedule(), p.getAutomaticActions(), p.getDockerfileMode(),
				p.getCustomDockerfile(), folders(p), p.getContainerImage());
		return new ProjectDetails(toSummary(p, openFindings(List.of(p))), settings, p.getPipeline().stream().map(ProjectServiceImpl::toView).toList());
	}

	private static List<ProjectFolder> folders(Project p) {
		return p.getFolders().stream().map(f -> new ProjectFolder(f.getPath(), f.getStack())).toList();
	}

	private static PipelineStageView toView(PipelineStageConfig c) {
		Stage s = c.getStage();
		return new PipelineStageView(s, s.phase(), s.tools(), c.getTool(), c.isEnabled(), c.getFailOn(), s.estimatedMinutes(),
				s.available());
	}
}
