package io.github.artemiscore.artemis.report.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportAuthor {
	SCHEDULE, USER;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
