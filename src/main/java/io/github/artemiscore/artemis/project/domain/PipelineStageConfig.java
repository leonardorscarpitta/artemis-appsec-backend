package io.github.artemiscore.artemis.project.domain;

import java.util.UUID;

import io.github.artemiscore.artemis.shared.domain.FailThreshold;
import io.github.artemiscore.artemis.shared.domain.Stage;
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
@Table(name = "pipeline_stage_config")
public class PipelineStageConfig {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id")
	private Project project;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stage stage;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private String tool;

	@Enumerated(EnumType.STRING)
	@Column(name = "fail_on")
	private FailThreshold failOn;

	protected PipelineStageConfig() {
	}

	PipelineStageConfig(Project project, Stage stage, boolean enabled, String tool, FailThreshold failOn) {
		this.id = UUID.randomUUID();
		this.project = project;
		this.stage = stage;
		update(enabled, tool, failOn);
	}

	public void update(boolean enabled, String tool, FailThreshold failOn) {
		if (!stage.tools().contains(tool)) {
			throw new IllegalArgumentException("Tool '%s' is not available for stage %s".formatted(tool, stage.wireValue()));
		}
		this.enabled = enabled;
		this.tool = tool;
		this.failOn = stage.canFail() ? (failOn == null ? stage.defaultFailOn() : failOn) : null;
	}

	public Stage getStage() {
		return stage;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getTool() {
		return tool;
	}

	public FailThreshold getFailOn() {
		return failOn;
	}
}
