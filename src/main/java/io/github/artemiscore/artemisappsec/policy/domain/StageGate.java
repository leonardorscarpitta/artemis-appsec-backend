package io.github.artemiscore.artemisappsec.policy.domain;

import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;
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
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Stage stage;

	@Enumerated(EnumType.STRING)
	@Column(name = "fail_on")
	private FailThreshold failOn;

	@Column(nullable = false)
	private boolean required;

	protected StageGate() {
	}

	public StageGate(UUID organizationId, Stage stage, FailThreshold failOn, boolean required) {
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
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
