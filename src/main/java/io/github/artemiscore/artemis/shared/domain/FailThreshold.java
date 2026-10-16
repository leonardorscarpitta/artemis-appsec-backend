package io.github.artemiscore.artemis.shared.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** Lowest severity that fails a pipeline stage; NEVER only reports. */
public enum FailThreshold {
	NEVER, CRITICAL, HIGH, MEDIUM, LOW;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
