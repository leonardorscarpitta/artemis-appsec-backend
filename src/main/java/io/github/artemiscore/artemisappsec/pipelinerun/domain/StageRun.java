package io.github.artemiscore.artemisappsec.pipelinerun.domain;

import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipeline_run_stage")
public class StageRun {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "run_id")
	private PipelineRun run;

	@Column(nullable = false)
	private int position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stage stage;

	@Column(nullable = false)
	private String tool;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StageStatus status;

	@Column(name = "duration_seconds", nullable = false)
	private int durationSeconds;

	@Column(nullable = false)
	private int findings;

	/** Severity threshold that failed the stage, if any. */
	@Enumerated(EnumType.STRING)
	@Column(name = "failed_by")
	private Severity failedBy;

	@Column(nullable = false)
	private String log;

	protected StageRun() {
	}

	StageRun(PipelineRun run, int position, Stage stage, String tool) {
		this.id = UUID.randomUUID();
		this.run = run;
		this.position = position;
		this.stage = stage;
		this.tool = tool;
		this.status = StageStatus.PENDING;
		this.log = "";
	}

	void skip() {
		if (status == StageStatus.PENDING || status == StageStatus.RUNNING) {
			status = StageStatus.SKIPPED;
			log = log.isEmpty() ? "Skipped: the run was cancelled." : log + "\nCancelled by user.";
		}
	}

	public int getPosition() {
		return position;
	}

	public Stage getStage() {
		return stage;
	}

	public String getTool() {
		return tool;
	}

	public StageStatus getStatus() {
		return status;
	}

	public int getDurationSeconds() {
		return durationSeconds;
	}

	public int getFindings() {
		return findings;
	}

	public Severity getFailedBy() {
		return failedBy;
	}

	public String getLog() {
		return log;
	}
}
