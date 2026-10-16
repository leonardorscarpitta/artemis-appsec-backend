package io.github.artemiscore.artemis.finding.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FindingEventType {
	DETECTED("detected"), STATUS_CHANGED("statusChanged");

	private final String code;

	FindingEventType(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
