package io.github.artemiscore.artemisappsec.finding.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** What a finding is about: a known vulnerability in a dependency, or an issue found in the repository itself. */
public enum FindingKind {
	VULNERABILITY, SECRET, CODE, CONFIG, LICENSE;

	/** Kinds identified by a dependency (package) rather than a file location. */
	public boolean isPackageBased() {
		return this == VULNERABILITY || this == LICENSE;
	}

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
