package io.github.artemiscore.artemis.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationEvent {
	CRITICAL_CVE("criticalCve"), PIPELINE_FAILED("pipelineFailed"), WEEKLY_REPORT("weeklyReport"), SLA_BREACH("slaBreach");

	private final String code;

	NotificationEvent(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
