package io.github.artemiscore.artemisappsec.project.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ScanSchedule {
	ON_PUSH("push"), EVERY_6_HOURS("6h"), EVERY_24_HOURS("24h"), EVERY_7_DAYS("7d");

	private final String code;

	ScanSchedule(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
