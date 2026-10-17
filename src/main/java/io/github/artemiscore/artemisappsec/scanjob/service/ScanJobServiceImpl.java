package io.github.artemiscore.artemisappsec.scanjob.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.finding.service.DetectedIssue;
import io.github.artemiscore.artemisappsec.finding.service.FindingService;
import io.github.artemiscore.artemisappsec.finding.service.IngestionResult;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageOutcome;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.StageStatus;
import io.github.artemiscore.artemisappsec.pipelinerun.service.ClaimedRun;
import io.github.artemiscore.artemisappsec.pipelinerun.service.PipelineRunService;
import io.github.artemiscore.artemisappsec.pipelinerun.service.RunJob;
import io.github.artemiscore.artemisappsec.pipelinerun.service.StageAssignment;
import io.github.artemiscore.artemisappsec.pipelinerun.service.StageResult;
import io.github.artemiscore.artemisappsec.project.service.AccessCheckJob;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.SshCredentials;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;

@Service
class ScanJobServiceImpl implements ScanJobService {

	static final int MAX_LOG_CHARS = 64 * 1024;
	private static final int MAX_MESSAGE_CHARS = 500;
	private static final int MAX_REASON_CHARS = 500;
	private static final int MAX_CLAIM_ATTEMPTS = 20;
	private static final String NO_DEPLOY_KEY = "No deploy key: open the repository's Deploy Key tab to set one up";

	private final PipelineRunService runs;
	private final FindingService findings;
	private final ProjectService projects;
	private final ArtemisAppSecProperties.Scanner scanner;

	ScanJobServiceImpl(PipelineRunService runs, FindingService findings, ProjectService projects,
			ArtemisAppSecProperties properties) {
		this.runs = runs;
		this.findings = findings;
		this.projects = projects;
		this.scanner = properties.scanner();
	}

	@Override
	@Transactional
	public Optional<Assignment> claim() {
		for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
			Optional<AccessCheckJob> check = projects.claimAccessCheck();
			if (check.isPresent()) {
				Optional<Assignment> assignment = accessCheck(check.get());
				if (assignment.isPresent()) {
					return assignment;
				}
				continue;
			}
			Optional<ClaimedRun> claimed = runs.claimNext();
			if (claimed.isEmpty()) {
				return Optional.empty();
			}
			Optional<Assignment> assignment = scan(claimed.get());
			if (assignment.isPresent()) {
				return assignment;
			}
		}
		return Optional.empty();
	}

	@Override
	@Transactional
	public void completeAccessCheck(UUID checkId, boolean ok, String message, String hostKey) {
		projects.completeAccessCheck(checkId, ok, Text.clean(message, MAX_MESSAGE_CHARS), hostKey);
	}

	private Optional<Assignment> accessCheck(AccessCheckJob check) {
		Optional<SshCredentials> credentials = projects.sshCredentials(check.projectId(), "access check " + check.checkId());
		if (credentials.isEmpty()) {
			projects.completeAccessCheck(check.checkId(), false, NO_DEPLOY_KEY, null);
			return Optional.empty();
		}
		return Optional.of(new AccessCheckAssignment("access-check", check.checkId(),
				new AccessCheckAssignment.Repository(check.name(), check.cloneUrl(), check.branch(), "ssh"), credentials.get()));
	}

	/** Builds the scan job, or fails the run when a private repository cannot be cloned yet. */
	private Optional<Assignment> scan(ClaimedRun run) {
		Optional<ProjectSummary> found = projects.findById(run.projectId());
		if (found.isEmpty()) {
			runs.fail(run.runId(), "the repository no longer exists");
			return Optional.empty();
		}
		ProjectSummary project = found.get();
		if (!"ssh".equals(project.access())) {
			runs.fail(run.runId(), "Only SSH repositories with an Artemis deploy key are scanned: add the repository again with its SSH URL and set up its key in the Deploy Key tab");
			return Optional.empty();
		}
		if (!projects.hostKeyKnown(project.id())) {
			runs.fail(run.runId(), "The Git server's host key is not known yet: run 'Test access' in the repository's Deploy Key tab first");
			return Optional.empty();
		}
		SshCredentials credentials = projects.sshCredentials(project.id(), "run #" + run.number()).orElse(null);
		if (credentials == null) {
			runs.fail(run.runId(), NO_DEPLOY_KEY);
			return Optional.empty();
		}
		List<JobAssignment.StageJob> stages = run.stages().stream()
			.map(s -> new JobAssignment.StageJob(s.id(), s.tool(), s.failOn()))
			.toList();
		return Optional.of(new JobAssignment("scan", run.runId(), run.number(),
				new JobAssignment.Repository(project.name(), project.url(), run.branch(),
						run.folders(), project.containerImage(), project.access()),
				credentials, stages, new JobAssignment.Limits(scanner.stageTimeoutSeconds(), scanner.maxRepoSizeMb())));
	}

	@Override
	@Transactional
	public void recordSource(UUID runId, String commitSha, String commitMessage) {
		runs.recordSource(runId, commitSha.toLowerCase(), Text.clean(commitMessage, MAX_MESSAGE_CHARS));
	}

	@Override
	@Transactional
	public StageStatus recordStage(UUID runId, Stage stage, StageReport report) {
		RunJob job = runs.job(runId);
		if (job.status() != RunStatus.RUNNING) {
			throw new BusinessRuleException("Run #%d is %s; stage results are no longer accepted"
				.formatted(job.number(), job.status().wireValue()));
		}
		StageAssignment assignment = job.pendingStages().stream()
			.filter(s -> s.id() == stage)
			.findFirst()
			.orElseThrow(() -> new BusinessRuleException("Stage %s of run #%d is not pending"
				.formatted(stage.wireValue(), job.number())));
		if (!assignment.tool().equals(report.tool())) {
			throw new IllegalArgumentException("Stage %s of run #%d must run %s, not %s"
				.formatted(stage.wireValue(), job.number(), assignment.tool(), report.tool()));
		}
		IngestionResult ingestion = report.status() == StageOutcome.COMPLETED
				? findings.recordDetections(job.projectId(), stage, assignment.tool(), report.findings().stream()
					.map(ScanJobServiceImpl::sanitize)
					.toList())
				: new IngestionResult(0, 0, 0, 0, null);
		return runs.recordStage(runId, new StageResult(stage, assignment.tool(), report.status(), report.durationSeconds(),
				Text.clean(report.log(), MAX_LOG_CHARS), ingestion.reported(), ingestion.highestSeverity(),
				ingestion.newFindings()));
	}

	@Override
	@Transactional
	public void complete(UUID runId) {
		runs.complete(runId);
	}

	@Override
	@Transactional
	public void fail(UUID runId, String reason, boolean accessLost) {
		String cleaned = Text.clean(reason, MAX_REASON_CHARS);
		if (accessLost) {
			projects.recordAccessLost(runs.job(runId).projectId(), cleaned);
		}
		runs.fail(runId, cleaned);
	}

	/** Bounds every field to its storage size and strips characters PostgreSQL cannot store. */
	private static DetectedIssue sanitize(DetectedIssue i) {
		return new DetectedIssue(i.kind(), Text.clean(i.ruleId(), 200), Text.clean(i.title(), 255),
				Text.clean(i.description(), 2000), i.severity(), Text.clean(i.cve(), 32), i.cvssScore(),
				Text.clean(i.cvssVector(), 128), Text.clean(i.cwe(), 16), Text.clean(i.packageName(), 255),
				Text.clean(i.packageVersion(), 64), Text.clean(i.fixedVersion(), 64), Text.clean(i.ecosystem(), 32),
				Text.clean(i.locationPath(), 1024), i.locationLine());
	}
}
