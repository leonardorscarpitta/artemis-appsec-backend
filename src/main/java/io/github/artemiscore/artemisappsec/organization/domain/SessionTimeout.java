package io.github.artemiscore.artemisappsec.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SessionTimeout {
	ONE_HOUR("1h"), EIGHT_HOURS("8h"), ONE_DAY("24h"), SEVEN_DAYS("7d");

	private final String code;

	SessionTimeout(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
