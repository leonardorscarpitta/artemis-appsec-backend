package io.github.artemiscore.artemisappsec.billing.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** Capabilities that depend on the organization's plan. */
public enum Feature {
	AI_TRIAGE("aiTriage");

	private final String code;

	Feature(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
