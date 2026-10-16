package io.github.artemiscore.artemisappsec.project.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Stack {
	REACT("React"), ANGULAR("Angular"), JAVA_SPRING("Java + Spring"), PYTHON_FLASK("Python + Flask"),
	PYTHON_FASTAPI("Python + FastAPI"), PYTHON_DJANGO("Python + Django");

	private final String label;

	Stack(String label) {
		this.label = label;
	}

	@JsonValue
	public String label() {
		return label;
	}
}
