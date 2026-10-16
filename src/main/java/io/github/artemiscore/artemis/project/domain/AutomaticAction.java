package io.github.artemiscore.artemis.project.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** What Artemis does automatically when a new CVE is detected. */
public enum AutomaticAction {
	OPEN_ISSUE("issue"), NOTIFY_EMAIL("email"), BLOCK_MERGE("block");

	private final String code;

	AutomaticAction(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
