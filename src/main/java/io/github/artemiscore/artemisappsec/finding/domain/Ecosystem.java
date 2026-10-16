package io.github.artemiscore.artemisappsec.finding.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Ecosystem {
	NPM, MAVEN, DEBIAN;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
