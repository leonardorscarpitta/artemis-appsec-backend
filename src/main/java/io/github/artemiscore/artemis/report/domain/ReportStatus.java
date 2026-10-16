package io.github.artemiscore.artemis.report.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportStatus {
	GENERATING, READY;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
