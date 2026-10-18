package io.github.artemiscore.artemisappsec.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** Optional capabilities the Artemis administrators enable per organization. */
public enum Feature {
	AI_TRIAGE("aiTriage"),
	SCHEDULED_SCANS("scheduledScans"),
	ORGANIZATION_RENAME("organizationRename");

	private final String code;

	Feature(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
