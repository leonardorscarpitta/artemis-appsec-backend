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
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;

@Service
class ScanJobServiceImpl implements ScanJobService {

	static final int MAX_LOG_CHARS = 64 * 1024;
	private static final int MAX_MESSAGE_CHARS = 500;
	private static final int MAX_REASON_CHARS = 500;

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
	public Optional<JobAssignment> claim() {
		Optional<ClaimedRun> claimed = runs.claimNext();
		if (claimed.isEmpty()) {
			return Optional.empty();
		}
		ClaimedRun run = claimed.get();
		Optional<ProjectSummary> project = projects.findById(run.projectId());
		if (project.isEmpty()) {
			runs.fail(run.runId(), "the repository no longer exists");
			return Optional.empty();
		}
		List<JobAssignment.StageJob> stages = run.stages().stream()
			.map(s -> new JobAssignment.StageJob(s.id(), s.tool(), s.failOn()))
			.toList();
		return Optional.of(new JobAssignment(run.runId(), run.number(),
				new JobAssignment.Repository(project.get().name(), project.get().url(), run.branch(),
						project.get().folders().stream().map(f -> f.path()).toList(), project.get().containerImage()), stages,
				new JobAssignment.Limits(scanner.stageTimeoutSeconds(), scanner.maxRepoSizeMb())));
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
	public void fail(UUID runId, String reason) {
		runs.fail(runId, Text.clean(reason, MAX_REASON_CHARS));
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
