package io.github.artemiscore.artemis.pipelinerun.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RunTrigger {
	PUSH("push"), SCHEDULE("schedule"), MANUAL("manual"), MERGE_REQUEST("mergeRequest");

	private final String code;

	RunTrigger(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}
}
