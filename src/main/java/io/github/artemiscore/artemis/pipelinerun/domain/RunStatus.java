package io.github.artemiscore.artemis.pipelinerun.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RunStatus {
	SUCCESS, FAILED, RUNNING, QUEUED, CANCELLED;

	public boolean isActive() {
		return this == RUNNING || this == QUEUED;
	}

	public boolean isFinished() {
		return this == SUCCESS || this == FAILED;
	}

	public static RunStatus fromWireValue(String value) {
		for (RunStatus status : values()) {
			if (status.wireValue().equalsIgnoreCase(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown run status '%s'".formatted(value));
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
