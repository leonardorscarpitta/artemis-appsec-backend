package io.github.artemiscore.artemisappsec.report.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** New reports are all {@code SUMMARY} with chosen {@link ReportSection}s; the other types remain for older reports. */
public enum ReportType {
	SUMMARY("summary"), COMPLIANCE("compliance"), DEPENDENCIES("dependencies");

	private final String code;

	ReportType(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
