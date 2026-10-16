package io.github.artemiscore.artemis.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationChannel {
	EMAIL("email"), SLACK("slack"), TEAMS("teams");

	private final String code;

	NotificationChannel(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
