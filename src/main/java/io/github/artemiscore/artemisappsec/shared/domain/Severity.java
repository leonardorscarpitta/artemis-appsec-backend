package io.github.artemiscore.artemisappsec.shared.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** CVSS v3 qualitative severity ratings, as used by the NVD. */
public enum Severity {
	CRITICAL, HIGH, MEDIUM, LOW;

	public static Severity fromCvss(double score) {
		if (score >= 9.0) {
			return CRITICAL;
		}
		if (score >= 7.0) {
			return HIGH;
		}
		if (score >= 4.0) {
			return MEDIUM;
		}
		return LOW;
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
