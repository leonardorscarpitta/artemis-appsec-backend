package io.github.artemiscore.artemisappsec.report.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportType {
	SUMMARY("summary"), COMPLIANCE("compliance");

	private final String code;

	ReportType(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
