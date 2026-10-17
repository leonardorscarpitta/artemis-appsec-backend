package io.github.artemiscore.artemisappsec.pipelinerun.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/** One execution of a repository's DevSecOps pipeline. */
@Entity
@Table(name = "pipeline_run")
public class PipelineRun {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, updatable = false)
	private long number;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(nullable = false)
	private String branch;

	@Column(name = "commit_sha", nullable = false)
	private String commitSha;

	@Column(name = "commit_message", nullable = false)
	private String commitMessage;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false)
	private RunTrigger trigger;

	@Column(nullable = false)
	private String actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RunStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@Column(name = "new_findings", nullable = false)
	private int newFindings;

	@OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<StageRun> stages = new ArrayList<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "pipeline_run_folder", joinColumns = @JoinColumn(name = "run_id"))
	@OrderColumn(name = "position")
	@Column(name = "path")
	private List<String> folders = new ArrayList<>();

	protected PipelineRun() {
	}

	/** A queued manual run of the given stages, in pipeline order. */
	public static PipelineRun queued(long number, UUID projectId, String branch, String commitSha, String commitMessage,
			String actor, List<StageSelection> selection, List<String> folders) {
		return queued(number, projectId, branch, commitSha, commitMessage, RunTrigger.MANUAL, actor, selection, folders);
	}

	public static PipelineRun queued(long number, UUID projectId, String branch, String commitSha, String commitMessage,
			RunTrigger trigger, String actor, List<StageSelection> selection, List<String> folders) {
		PipelineRun run = new PipelineRun();
		run.folders.addAll(folders);
		run.id = UUID.randomUUID();
		run.number = number;
		run.projectId = projectId;
		run.branch = branch;
		run.commitSha = commitSha;
		run.commitMessage = commitMessage;
		run.trigger = trigger;
		run.actor = actor;
		run.status = RunStatus.QUEUED;
		run.startedAt = Instant.now();
		for (int i = 0; i < selection.size(); i++) {
			StageSelection s = selection.get(i);
			run.stages.add(new StageRun(run, i, s.stage(), s.tool(), s.failOn()));
		}
		return run;
	}

	/** Monorepo folders this run scans; empty for the whole repository. */
	public List<String> getFolders() {
		return List.copyOf(folders);
	}

	/** Called when the scanner runner picks the run up; stages it cannot execute are skipped right away. */
	public void start(Instant now) {
		requireStatus(RunStatus.QUEUED, "start");
		status = RunStatus.RUNNING;
		startedAt = now;
		stages.stream()
			.filter(s -> !s.getStage().available())
			.forEach(s -> s.skip("Skipped: this stage is not available in the scanner runner yet."));
	}

	public void recordSource(String sha, String message) {
		requireStatus(RunStatus.RUNNING, "record its source");
		this.commitSha = sha;
		this.commitMessage = message;
	}

	public StageRun recordStage(Stage stage, String tool, StageOutcome outcome, int durationSeconds, String log,
			int findings, Severity highest, int newFindings) {
		requireStatus(RunStatus.RUNNING, "record stage results");
		StageRun target = stages.stream()
			.filter(s -> s.getStage() == stage)
			.findFirst()
			.orElseThrow(() -> new BusinessRuleException("Run #%d has no %s stage".formatted(number, stage.wireValue())));
		if (!target.isPending()) {
			throw new BusinessRuleException("Stage %s of run #%d was already reported".formatted(stage.wireValue(), number));
		}
		if (!target.getTool().equals(tool)) {
			throw new IllegalArgumentException("Stage %s of run #%d must run %s, not %s"
				.formatted(stage.wireValue(), number, target.getTool(), tool));
		}
		target.record(outcome, durationSeconds, log, findings, highest);
		this.newFindings += Math.max(0, newFindings);
		return target;
	}

	public void complete(Instant now) {
		requireStatus(RunStatus.RUNNING, "complete");
		// Unavailable stages were skipped when the run started; anything still pending was never reported, so the
		// run cannot count as a pass (e.g. the runner crashed or was cut short).
		boolean incomplete = stages.stream().anyMatch(s -> s.getStatus() == StageStatus.PENDING || s.getStatus() == StageStatus.RUNNING);
		stages.forEach(s -> s.skip("Skipped: the runner did not report this stage (incomplete run)."));
		boolean failed = stages.stream().anyMatch(s -> s.getStatus() == StageStatus.FAILED);
		status = failed || incomplete ? RunStatus.FAILED : RunStatus.SUCCESS;
		durationSeconds = elapsedSince(now);
	}

	/** Fails the run without stage results (e.g. the repository could not be cloned, or the runner timed out). */
	public void fail(String reason, Instant now) {
		if (!status.isActive()) {
			throw new BusinessRuleException("Run #%d is %s and cannot fail".formatted(number, status.wireValue()));
		}
		stages.forEach(s -> s.skip("Skipped: " + reason));
		status = RunStatus.FAILED;
		durationSeconds = elapsedSince(now);
	}

	private int elapsedSince(Instant now) {
		return (int) Math.max(0, Duration.between(startedAt, now).getSeconds());
	}

	private void requireStatus(RunStatus expected, String action) {
		if (status != expected) {
			throw new BusinessRuleException("Run #%d is %s and cannot %s".formatted(number, status.wireValue(), action));
		}
	}

	public void cancel() {
		if (!status.isActive()) {
			throw new BusinessRuleException("Run #%d is %s and cannot be cancelled".formatted(number, status.wireValue()));
		}
		status = RunStatus.CANCELLED;
		stages.forEach(s -> s.skip("Skipped: the run was cancelled."));
	}

	public int totalFindings() {
		return stages.stream().mapToInt(StageRun::getFindings).sum();
	}

	public boolean failedByPolicy() {
		return stages.stream().anyMatch(s -> s.getFailedBy() != null);
	}

	public List<StageRun> getStages() {
		return stages.stream().sorted(Comparator.comparingInt(StageRun::getPosition)).toList();
	}

	public UUID getId() {
		return id;
	}

	public long getNumber() {
		return number;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getBranch() {
		return branch;
	}

	public String getCommitSha() {
		return commitSha;
	}

	public String getCommitMessage() {
		return commitMessage;
	}

	public RunTrigger getTrigger() {
		return trigger;
	}

	public String getActor() {
		return actor;
	}

	public RunStatus getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public int getDurationSeconds() {
		return durationSeconds;
	}

	public int getNewFindings() {
		return newFindings;
	}

	public record StageSelection(Stage stage, String tool, FailThreshold failOn) {
	}
}
