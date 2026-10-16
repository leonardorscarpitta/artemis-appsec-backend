package io.github.artemiscore.artemisappsec.shared.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** Lowest severity that fails a pipeline stage; NEVER only reports. */
public enum FailThreshold {
	NEVER, CRITICAL, HIGH, MEDIUM, LOW;

	/** Lowest severity that fails the stage, or null for {@link #NEVER}. */
	public Severity severity() {
		return this == NEVER ? null : Severity.valueOf(name());
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
