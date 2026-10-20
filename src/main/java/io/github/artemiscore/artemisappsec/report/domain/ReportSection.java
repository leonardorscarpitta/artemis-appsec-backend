package io.github.artemiscore.artemisappsec.report.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** Optional report topics; the figures (KPIs, trend, severities, top risks, OWASP) are always included. */
public enum ReportSection {
	SUMMARY("summary"), RECOMMENDATIONS("recommendations"), SBOM("sbom");

	private final String code;

	ReportSection(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
