package io.github.artemiscore.artemisappsec.project.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** Where a repository's stack came from; automatic detection never overrides a manual choice. */
public enum StackSource {
	PENDING, DETECTED, MANUAL;

	@JsonValue
	public String wire() {
		return name().toLowerCase(Locale.ROOT);
	}
}
