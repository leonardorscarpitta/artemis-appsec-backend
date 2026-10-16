package io.github.artemiscore.artemis.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Permission {
	VIEW_FINDINGS("viewFindings"), TRIAGE("triage"), PIPELINES("pipelines"), INTEGRATIONS("integrations"), TEAM("team"), BILLING("billing");

	private final String code;

	Permission(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
