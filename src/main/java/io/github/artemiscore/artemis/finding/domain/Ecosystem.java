package io.github.artemiscore.artemis.finding.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Ecosystem {
	NPM, MAVEN, DEBIAN;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
