package io.github.artemiscore.artemisappsec.pipelinerun.service;

import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.branchContains;
import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.hasProject;
import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.hasStatus;
import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.inProjects;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.Feature;
import io.github.artemiscore.artemisappsec.organization.service.OrganizationFeatures;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.PipelineRun;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.PipelineRun.StageSelection;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunTrigger;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageRun;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunRepository;
import io.github.artemiscore.artemisappsec.pipelinerun.persistence.RunQuota;
import io.github.artemiscore.artemisappsec.pipelinerun.persistence.RunSboms;
import io.github.artemiscore.artemisappsec.project.service.ProjectDetails;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.domain.AccessStatus;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;
import io.github.artemiscore.artemisappsec.usage.service.UsageService;

@Service
@Transactional(readOnly = true)
class PipelineRunServiceImpl implements PipelineRunService {

	private static final Duration SUMMARY_WINDOW = Duration.ofDays(7);
	/** Placeholders until the runner reports the commit it actually cloned. */
	private static final String PENDING_COMMIT = "pending";
	private static final String PENDING_MESSAGE = "Waiting for the scanner runner";
	private static final String SCHEDULER_ACTOR = "Artemis scheduler";
	private static final List<RunStatus> ACTIVE = List.of(RunStatus.QUEUED, RunStatus.RUNNING);

	private final PipelineRunRepository runs;
	private final ProjectService projects;
	private final UsageService usage;
	private final OrganizationFeatures features;
	private final RunQuota quota;
	private final RunSboms sboms;
	private final Clock clock;

	PipelineRunServiceImpl(PipelineRunRepository runs, ProjectService projects, UsageService usage, OrganizationFeatures features,
			RunQuota quota, RunSboms sboms) {
		this.runs = runs;
		this.quota = quota;
		this.sboms = sboms;
		this.projects = projects;
		this.usage = usage;
		this.features = features;
		this.clock = Clock.systemUTC();
	}

	@Override
	public List<RunView> find(UUID organizationId, RunFilter filter) {
		Map<UUID, ProjectSummary> names = projectNames(organizationId);
		if (names.isEmpty()) {
			return List.of();
		}
		UUID projectId = null;
		if (filter.repository() != null && !filter.repository().isBlank()) {
			// A removed repository may share its name with the one connected again; the connected one wins.
			projectId = names.values().stream()
				.filter(p -> p.name().equals(filter.repository()))
				.sorted(Comparator.comparing(ProjectSummary::removed))
				.map(ProjectSummary::id)
				.findFirst()
				.orElse(null);
			if (projectId == null) {
				return List.of();
			}
		}
		Specification<PipelineRun> spec = Specification.allOf(inProjects(names.keySet()), hasProject(projectId), hasStatus(filter.status()),
				branchContains(filter.branch()));
		return runs.findAll(spec, Sort.by(Sort.Direction.DESC, "number")).stream().map(r -> toView(r, names)).toList();
	}

	@Override
	public RunsSummary summary(UUID organizationId) {
		Map<UUID, ProjectSummary> names = projectNames(organizationId);
		List<PipelineRun> recent = names.isEmpty() ? List.of()
				: runs.findByProjectIdInAndStartedAtAfter(names.keySet(), Instant.now(clock).minus(SUMMARY_WINDOW));
		long finished = recent.stream().filter(r -> r.getStatus().isFinished()).count();
		long succeeded = recent.stream().filter(r -> r.getStatus() == RunStatus.SUCCESS).count();
		double avgDuration = recent.stream()
			.filter(r -> r.getDurationSeconds() > 0 && r.getStatus() != RunStatus.RUNNING)
			.mapToInt(PipelineRun::getDurationSeconds)
			.average()
			.orElse(0);
		int failedByPolicy = (int) recent.stream().filter(PipelineRun::failedByPolicy).count();
		int successRate = finished == 0 ? 0 : (int) Math.round(succeeded * 100.0 / finished);
		return new RunsSummary(recent.size(), successRate, (int) Math.round(avgDuration), failedByPolicy);
	}

	@Override
	public List<RunActivity> activity(UUID organizationId, LocalDate from, ZoneId zone) {
		Map<UUID, ProjectSummary> names = projectNames(organizationId);
		if (names.isEmpty()) {
			return List.of();
		}
		Map<LocalDate, long[]> byDay = new TreeMap<>();
		for (Object[] row : runs.dailyStatusCounts(names.keySet(), from.atStartOfDay(zone).toInstant(), zone.getId())) {
			long[] counts = byDay.computeIfAbsent(LocalDate.parse((String) row[0]), d -> new long[3]);
			int slot = switch (RunStatus.valueOf((String) row[1])) {
				case SUCCESS -> 0;
				case FAILED -> 1;
				default -> 2;
			};
			counts[slot] += ((Number) row[2]).longValue();
		}
		return byDay.entrySet().stream()
			.map(e -> new RunActivity(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
			.toList();
	}

	@Override
	public RunDetails get(UUID organizationId, long number) {
		Map<UUID, ProjectSummary> names = projectNames(organizationId);
		return toDetails(load(number, names), names);
	}

	@Override
	@Transactional
	public RunDetails rerun(UUID organizationId, long number, String actor) {
		PipelineRun source = load(number, projectNames(organizationId));
		ProjectSummary project = projects.findById(source.getProjectId())
			.orElseThrow(() -> new ResourceNotFoundException("Repository", source.getProjectId()));
		if (project.removed()) {
			throw new BusinessRuleException("Repository '%s' was removed; its runs are kept as history and cannot be re-run"
				.formatted(project.name()));
		}
		requireVerifiedAccess(project);
		LocalDate quotaMonth = usage.consumeScan(organizationId);
		List<StageSelection> stages = enabledStages(organizationId, project.name());
		// Folders removed from the repository since then are dropped; none left means all of them.
		List<String> current = project.folders().stream().map(f -> f.path()).toList();
		List<String> folders = selectFolders(project, source.getFolders().stream().filter(current::contains).toList());
		PipelineRun run = PipelineRun.queued(runs.nextNumber(), source.getProjectId(), source.getBranch(),
				source.getCommitSha(), source.getCommitMessage(), actor, stages, folders);
		run.reserveQuota(quotaMonth);
		return toDetails(runs.save(run), Map.of(project.id(), project));
	}

	@Override
	@Transactional
	public RunDetails queueScan(UUID organizationId, String repository, String actor, List<String> folders) {
		ProjectDetails project = projects.getByName(organizationId, repository);
		requireVerifiedAccess(project.project());
		return queue(organizationId, project.project(), RunTrigger.MANUAL, actor, selectFolders(project.project(), folders));
	}

	@Override
	@Transactional
	public Optional<RunDetails> queueScheduledScan(UUID organizationId, String repository) {
		if (!features.isEnabled(organizationId, Feature.SCHEDULED_SCANS)) {
			return Optional.empty();
		}
		ProjectSummary project = projects.getByName(organizationId, repository).project();
		if (project.accessStatus() != AccessStatus.VERIFIED || runs.existsByProjectIdAndStatusIn(project.id(), ACTIVE)) {
			return Optional.empty();
		}
		return Optional.of(queue(organizationId, project, RunTrigger.SCHEDULE, SCHEDULER_ACTOR, selectFolders(project, null)));
	}

	private RunDetails queue(UUID organizationId, ProjectSummary project, RunTrigger trigger, String actor,
			List<String> folders) {
		LocalDate quotaMonth = usage.consumeScan(organizationId);
		List<StageSelection> stages = enabledStages(organizationId, project.name());
		PipelineRun run = PipelineRun.queued(runs.nextNumber(), project.id(), project.branch(), PENDING_COMMIT,
				PENDING_MESSAGE, trigger, actor, stages, folders);
		run.reserveQuota(quotaMonth);
		return toDetails(runs.save(run), Map.of(project.id(), project));
	}

	@Override
	@Transactional
	public Optional<ClaimedRun> claimNext() {
		Optional<UUID> next = runs.lockNextQueued();
		if (next.isEmpty()) {
			return Optional.empty();
		}
		PipelineRun run = loadById(next.get());
		run.start(Instant.now(clock));
		List<StageAssignment> pending = pendingStages(run);
		return Optional.of(new ClaimedRun(run.getId(), run.getNumber(), run.getProjectId(), run.getBranch(), pending,
				run.getFolders()));
	}

	@Override
	public RunJob job(UUID runId) {
		PipelineRun run = loadById(runId);
		return new RunJob(run.getId(), run.getNumber(), run.getProjectId(), run.getStatus(), pendingStages(run));
	}

	@Override
	@Transactional
	public void recordSource(UUID runId, String commitSha, String commitMessage) {
		loadById(runId).recordSource(commitSha, commitMessage);
	}

	@Override
	@Transactional
	public StageStatus recordStage(UUID runId, StageResult r) {
		quota.settle(runId);
		StageRun stage = loadById(runId).recordStage(r.stage(), r.tool(), r.outcome(), r.durationSeconds(), r.log(),
				r.findings(), r.highestSeverity(), r.newFindings());
		return stage.getStatus();
	}

	@Override
	@Transactional
	public void storeSbom(UUID runId, String folder, byte[] gzipped, int sizeBytes, int components) {
		PipelineRun run = loadById(runId);
		if (run.getStatus() != RunStatus.RUNNING) {
			throw new BusinessRuleException("Run #%d is %s; SBOMs are no longer accepted"
				.formatted(run.getNumber(), run.getStatus().wireValue()));
		}
		if (run.getFolders().isEmpty() ? !folder.isEmpty() : !run.getFolders().contains(folder)) {
			throw new IllegalArgumentException("Run #%d does not scan folder '%s'".formatted(run.getNumber(), folder));
		}
		sboms.save(runId, folder, gzipped, sizeBytes, components);
	}

	@Override
	public SbomFile sbom(UUID organizationId, long number, String folder) {
		Map<UUID, ProjectSummary> names = projectNames(organizationId);
		PipelineRun run = load(number, names);
		RunSboms.Content content = sboms.content(run.getId(), folder)
			.orElseThrow(() -> new ResourceNotFoundException("Pipeline run SBOM", number));
		return new SbomFile(names.get(run.getProjectId()).name(), number, folder, content.gzipped(), content.sizeBytes());
	}

	@Override
	public List<LatestSbom> latestSboms(UUID organizationId, String repository) {
		Map<UUID, ProjectSummary> connected = projectNames(organizationId).values().stream()
			.filter(p -> !p.removed() && (repository == null || p.name().equals(repository)))
			.collect(Collectors.toMap(ProjectSummary::id, p -> p));
		return sboms.latestPerProject(connected.keySet()).stream()
			.map(l -> new LatestSbom(connected.get(l.projectId()).name(), l.runNumber(), l.startedAt(), l.folder(), l.gzipped()))
			.toList();
	}

	@Override
	@Transactional
	public void complete(UUID runId) {
		PipelineRun run = loadById(runId);
		Instant now = Instant.now(clock);
		run.complete(now);
		quota.settle(runId);
		projects.recordScan(run.getProjectId(), now);
	}

	@Override
	@Transactional
	public void fail(UUID runId, String reason) {
		PipelineRun run = loadById(runId);
		run.fail(reason, Instant.now(clock));
		refundIfNotScanned(run);
	}

	@Override
	@Transactional
	public int failStaleRuns(Duration maxDuration) {
		Instant now = Instant.now(clock);
		List<PipelineRun> stale = runs.findByStatusAndStartedAtBefore(RunStatus.RUNNING, now.minus(maxDuration));
		stale.forEach(r -> {
			r.fail("the scanner runner did not finish within %d minutes".formatted(maxDuration.toMinutes()), now);
			refundIfNotScanned(r);
		});
		return stale.size();
	}

	private List<StageSelection> enabledStages(UUID organizationId, String repository) {
		List<StageSelection> stages = projects.effectivePipeline(organizationId, repository).stream()
			.filter(s -> s.enabled())
			.map(s -> new StageSelection(s.id(), s.tool(), s.failOn()))
			.toList();
		if (stages.isEmpty()) {
			throw new BusinessRuleException("Repository '%s' has no enabled pipeline stages".formatted(repository));
		}
		return stages;
	}

	private static List<StageAssignment> pendingStages(PipelineRun run) {
		return run.getStages().stream()
			.filter(s -> s.getStatus() == StageStatus.PENDING)
			.map(s -> new StageAssignment(s.getStage(), s.getTool(), s.getFailOn()))
			.toList();
	}

	private PipelineRun loadById(UUID id) {
		return runs.findWithStagesById(id).orElseThrow(() -> new ResourceNotFoundException("Pipeline run", id));
	}

	@Override
	@Transactional
	public RunDetails cancel(UUID organizationId, long number) {
		Map<UUID, ProjectSummary> names = projectNames(organizationId);
		PipelineRun run = load(number, names);
		run.cancel();
		refundIfNotScanned(run);
		return toDetails(run, names);
	}

	@Override
	@Transactional
	public int cancelActive(UUID projectId) {
		List<PipelineRun> active = runs.findByProjectIdAndStatusIn(projectId, List.of(RunStatus.QUEUED, RunStatus.RUNNING));
		active.forEach(run -> {
			run.cancel();
			refundIfNotScanned(run);
		});
		return active.size();
	}


	/** A run that ended before the runner reported any stage gives its monthly scan back. */
	private void refundIfNotScanned(PipelineRun run) {
		quota.release(run.getId()).ifPresent(month -> {
			projects.findById(run.getProjectId()).ifPresent(p -> usage.refundScan(p.organizationId(), month));
			run.markQuotaRefunded();
		});
	}

	private PipelineRun load(long number, Map<UUID, ProjectSummary> organizationProjects) {
		return runs.findByNumber(number)
			.filter(r -> organizationProjects.containsKey(r.getProjectId()))
			.orElseThrow(() -> new ResourceNotFoundException("Pipeline run", number));
	}

	/** Scans need a working deploy key: refusing early keeps the run (and the monthly quota) for when access works. */
	private static void requireVerifiedAccess(ProjectSummary project) {
		if (project.accessStatus() != AccessStatus.VERIFIED) {
			throw new BusinessRuleException("Set up access first: add the deploy key from the repository's Deploy Key tab and select 'Test access'");
		}
	}

	/**
	 * The monorepo folders a run scans, in the repository's order: the requested ones (each must be one of the
	 * repository's folders, else 400) or, when none are requested, all of them. Empty for non-monorepos.
	 */
	private static List<String> selectFolders(ProjectSummary project, List<String> requested) {
		List<String> all = project.folders().stream().map(f -> f.path()).toList();
		if (requested == null || requested.isEmpty()) {
			return all;
		}
		List<String> unknown = requested.stream().filter(f -> !all.contains(f)).toList();
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("Unknown monorepo folder(s): " + String.join(", ", unknown));
		}
		return all.stream().filter(requested::contains).toList();
	}

	/** Includes removed repositories, whose runs stay visible as history. */
	private Map<UUID, ProjectSummary> projectNames(UUID organizationId) {
		return projects.findAllIncludingRemoved(organizationId).stream().collect(Collectors.toMap(ProjectSummary::id, p -> p));
	}

	private static RunView toView(PipelineRun r, Map<UUID, ProjectSummary> names) {
		ProjectSummary project = names.get(r.getProjectId());
		return new RunView(r.getNumber(), project == null ? "unknown" : project.name(), project != null && project.removed(),
				r.getBranch(), r.getCommitSha(), r.getCommitMessage(), r.getTrigger(), r.getActor(), r.getStatus(),
				r.getStartedAt(), r.getDurationSeconds(), r.getNewFindings(), r.totalFindings(), r.failedByPolicy(),
				r.getFolders(), r.isQuotaRefunded());
	}

	private RunDetails toDetails(PipelineRun r, Map<UUID, ProjectSummary> names) {
		List<SbomView> stored = sboms.list(r.getId()).stream().map(e -> new SbomView(e.folder(), e.components(), e.sizeBytes())).toList();
		return new RunDetails(toView(r, names), r.getStages().stream().map(PipelineRunServiceImpl::toStageView).toList(), stored);
	}

	private static StageRunView toStageView(StageRun s) {
		List<String> log = s.getLog().isEmpty() ? List.of() : Arrays.asList(s.getLog().split("\n"));
		return new StageRunView(s.getStage(), s.getStage().phase(), s.getTool(), s.getStatus(), s.getDurationSeconds(),
				s.getFindings(), s.getFailedBy(), log);
	}

}
