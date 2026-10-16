package io.github.artemiscore.artemis.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IntegrationStatus {
	CONNECTED("connected"), DISCONNECTED("disconnected"), ERROR("error");

	private final String code;

	IntegrationStatus(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
