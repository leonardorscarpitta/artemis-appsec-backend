package io.github.artemiscore.artemisappsec.pipelinerun.service;

import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.branchContains;
import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.hasProject;
import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.hasStatus;
import static io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunSpecifications.inProjects;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.billing.service.BillingService;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.PipelineRun;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.PipelineRun.StageSelection;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageRun;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.persistence.PipelineRunRepository;
import io.github.artemiscore.artemisappsec.pipelinerun.persistence.RunQuota;
import io.github.artemiscore.artemisappsec.project.service.ProjectDetails;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.domain.AccessStatus;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class PipelineRunServiceImpl implements PipelineRunService {

	private static final Duration SUMMARY_WINDOW = Duration.ofDays(7);
	/** Placeholders until the runner reports the commit it actually cloned. */
	private static final String PENDING_COMMIT = "pending";
	private static final String PENDING_MESSAGE = "Waiting for the scanner runner";

	private final PipelineRunRepository runs;
	private final ProjectService projects;
	private final BillingService billing;
	private final RunQuota quota;
	private final Clock clock;

	PipelineRunServiceImpl(PipelineRunRepository runs, ProjectService projects, BillingService billing, RunQuota quota) {
		this.runs = runs;
		this.quota = quota;
		this.projects = projects;
		this.billing = billing;
		this.clock = Clock.systemUTC();
	}

	@Override
	public List<RunView> find(UUID organizationId, RunFilter filter) {
		Map<UUID, String> names = projectNames(organizationId);
		if (names.isEmpty()) {
			return List.of();
		}
		UUID projectId = null;
		if (filter.repository() != null && !filter.repository().isBlank()) {
			projectId = names.entrySet().stream()
				.filter(e -> e.getValue().equals(filter.repository()))
				.map(Map.Entry::getKey)
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
		Map<UUID, String> names = projectNames(organizationId);
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
	public RunDetails get(UUID organizationId, long number) {
		Map<UUID, String> names = projectNames(organizationId);
		return toDetails(load(number, names), names);
	}

	@Override
	@Transactional
	public RunDetails rerun(UUID organizationId, long number, String actor) {
		PipelineRun source = load(number, projectNames(organizationId));
		LocalDate quotaMonth = billing.consumeScan(organizationId);
		ProjectSummary project = projects.findById(source.getProjectId())
			.orElseThrow(() -> new ResourceNotFoundException("Repository", source.getProjectId()));
		requireVerifiedAccess(project);
		List<StageSelection> stages = enabledStages(organizationId, project.name());
		// Folders removed from the repository since then are dropped; none left means all of them.
		List<String> current = project.folders().stream().map(f -> f.path()).toList();
		List<String> folders = selectFolders(project, source.getFolders().stream().filter(current::contains).toList());
		PipelineRun run = PipelineRun.queued(runs.nextNumber(), source.getProjectId(), source.getBranch(),
				source.getCommitSha(), source.getCommitMessage(), actor, stages, folders);
		run.reserveQuota(quotaMonth);
		return toDetails(runs.save(run), Map.of(project.id(), project.name()));
	}

	@Override
	@Transactional
	public RunDetails queueScan(UUID organizationId, String repository, String actor, List<String> folders) {
		ProjectDetails project = projects.getByName(organizationId, repository);
		requireVerifiedAccess(project.project());
		List<String> selected = selectFolders(project.project(), folders);
		LocalDate quotaMonth = billing.consumeScan(organizationId);
		List<StageSelection> stages = enabledStages(organizationId, repository);
		PipelineRun run = PipelineRun.queued(runs.nextNumber(), project.project().id(), project.project().branch(),
				PENDING_COMMIT, PENDING_MESSAGE, actor, stages, selected);
		run.reserveQuota(quotaMonth);
		return toDetails(runs.save(run), Map.of(project.project().id(), repository));
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
		Map<UUID, String> names = projectNames(organizationId);
		PipelineRun run = load(number, names);
		run.cancel();
		refundIfNotScanned(run);
		return toDetails(run, names);
	}

	/** A run that ended before the runner reported any stage gives its monthly scan back. */
	private void refundIfNotScanned(PipelineRun run) {
		quota.release(run.getId()).ifPresent(month -> {
			projects.findById(run.getProjectId()).ifPresent(p -> billing.refundScan(p.organizationId(), month));
			run.markQuotaRefunded();
		});
	}

	private PipelineRun load(long number, Map<UUID, String> organizationProjects) {
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

	private Map<UUID, String> projectNames(UUID organizationId) {
		return projects.findAll(organizationId).stream().collect(Collectors.toMap(ProjectSummary::id, ProjectSummary::name));
	}

	private static RunView toView(PipelineRun r, Map<UUID, String> names) {
		return new RunView(r.getNumber(), names.getOrDefault(r.getProjectId(), "unknown"), r.getBranch(), r.getCommitSha(),
				r.getCommitMessage(), r.getTrigger(), r.getActor(), r.getStatus(), r.getStartedAt(), r.getDurationSeconds(),
				r.getNewFindings(), r.totalFindings(), r.failedByPolicy(), r.getFolders(), r.isQuotaRefunded());
	}

	private static RunDetails toDetails(PipelineRun r, Map<UUID, String> names) {
		return new RunDetails(toView(r, names), r.getStages().stream().map(PipelineRunServiceImpl::toStageView).toList());
	}

	private static StageRunView toStageView(StageRun s) {
		List<String> log = s.getLog().isEmpty() ? List.of() : Arrays.asList(s.getLog().split("\n"));
		return new StageRunView(s.getStage(), s.getStage().phase(), s.getTool(), s.getStatus(), s.getDurationSeconds(),
				s.getFindings(), s.getFailedBy(), log);
	}

}
