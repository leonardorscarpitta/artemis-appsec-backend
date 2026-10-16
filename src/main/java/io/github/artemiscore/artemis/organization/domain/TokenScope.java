package io.github.artemiscore.artemis.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TokenScope {
	FINDINGS_READ("findings:read"), SCANS_WRITE("scans:write"), REPORTS_READ("reports:read"), PIPELINES_WRITE("pipelines:write");

	private final String code;

	TokenScope(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
