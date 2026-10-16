package io.github.artemiscore.artemis.organization.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** How long data is kept before being purged. */
public enum RetentionPeriod {
	DAYS_90("90"), DAYS_180("180"), DAYS_365("365"), DAYS_730("730"), FOREVER("forever");

	private final String code;

	RetentionPeriod(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
