package io.github.artemiscore.artemis.pipelinerun.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import io.github.artemiscore.artemis.shared.domain.Stage;
import io.github.artemiscore.artemis.shared.error.BusinessRuleException;
import jakarta.persistence.CascadeType;
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

	protected PipelineRun() {
	}

	/** A queued manual run of the given stages, in pipeline order. */
	public static PipelineRun queued(long number, UUID projectId, String branch, String commitSha, String commitMessage,
			String actor, List<StageSelection> selection) {
		PipelineRun run = new PipelineRun();
		run.id = UUID.randomUUID();
		run.number = number;
		run.projectId = projectId;
		run.branch = branch;
		run.commitSha = commitSha;
		run.commitMessage = commitMessage;
		run.trigger = RunTrigger.MANUAL;
		run.actor = actor;
		run.status = RunStatus.QUEUED;
		run.startedAt = Instant.now();
		for (int i = 0; i < selection.size(); i++) {
			run.stages.add(new StageRun(run, i, selection.get(i).stage(), selection.get(i).tool()));
		}
		return run;
	}

	public void cancel() {
		if (!status.isActive()) {
			throw new BusinessRuleException("Run #%d is %s and cannot be cancelled".formatted(number, status.wireValue()));
		}
		status = RunStatus.CANCELLED;
		stages.forEach(StageRun::skip);
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

	public record StageSelection(Stage stage, String tool) {
	}
}
