package io.github.artemiscore.artemisappsec.shared.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Phase {
	SOURCE, BUILD, DEPLOY;

	@JsonValue
	public String wireValue() {
		return name().toLowerCase();
	}
}
