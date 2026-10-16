package io.github.artemiscore.artemis.project.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DockerfileMode {
	RECOMMENDED, CUSTOM;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
