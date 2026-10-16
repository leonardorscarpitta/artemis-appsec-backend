package io.github.artemiscore.artemis.policy.domain;

import io.github.artemiscore.artemis.shared.domain.FailThreshold;
import io.github.artemiscore.artemis.shared.domain.Stage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Organization-wide default fail threshold for a stage and whether repositories may disable it. */
@Entity
@Table(name = "policy_stage_gate")
public class StageGate {

	@Id
	@Enumerated(EnumType.STRING)
	private Stage stage;

	@Enumerated(EnumType.STRING)
	@Column(name = "fail_on")
	private FailThreshold failOn;

	@Column(nullable = false)
	private boolean required;

	protected StageGate() {
	}

	public StageGate(Stage stage, FailThreshold failOn, boolean required) {
		this.stage = stage;
		update(failOn, required);
	}

	public void update(FailThreshold failOn, boolean required) {
		if (stage.canFail() == (failOn == null)) {
			throw new IllegalArgumentException(stage.canFail()
					? "Stage %s requires a fail threshold".formatted(stage.wireValue())
					: "Stage %s never fails the pipeline and takes no threshold".formatted(stage.wireValue()));
		}
		this.failOn = failOn;
		this.required = required;
	}

	public Stage getStage() {
		return stage;
	}

	public FailThreshold getFailOn() {
		return failOn;
	}

	public boolean isRequired() {
		return required;
	}
}
