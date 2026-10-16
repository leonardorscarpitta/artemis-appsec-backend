package io.github.artemiscore.artemisappsec.pipelinerun.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum StageStatus {
	PASSED, FAILED, WARNING, RUNNING, PENDING, SKIPPED;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
