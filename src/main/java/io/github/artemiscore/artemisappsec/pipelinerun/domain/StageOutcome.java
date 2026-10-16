package io.github.artemiscore.artemisappsec.pipelinerun.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** What the scanner runner reports for a stage; the API derives the final {@link StageStatus}. */
public enum StageOutcome {
	COMPLETED, SKIPPED, ERROR;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
