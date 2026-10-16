package io.github.artemiscore.artemis.project.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Stack {
	REACT("React"), ANGULAR("Angular"), JAVA_SPRING("Java + Spring");

	private final String label;

	Stack(String label) {
		this.label = label;
	}

	@JsonValue
	public String label() {
		return label;
	}
}
