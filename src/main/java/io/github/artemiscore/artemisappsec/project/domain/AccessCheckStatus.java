package io.github.artemiscore.artemisappsec.project.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AccessCheckStatus {
	QUEUED, RUNNING, OK, FAILED;

	@JsonValue
	public String code() {
		return name().toLowerCase();
	}
}
