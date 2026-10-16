package io.github.artemiscore.artemisappsec.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IntegrationCategory {
	SCM("scm"), CI("ci"), NOTIFICATIONS("notifications"), ISSUES("issues"), VULNERABILITY_DATA("vulnData");

	private final String code;

	IntegrationCategory(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
