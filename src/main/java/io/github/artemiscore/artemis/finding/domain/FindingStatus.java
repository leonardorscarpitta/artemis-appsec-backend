package io.github.artemiscore.artemis.finding.domain;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FindingStatus {
	OPEN("open"), TRIAGED("triaged"), FALSE_POSITIVE("falsePositive"), ACCEPTED("accepted"), FIXED("fixed");

	private final String code;

	FindingStatus(String code) {
		this.code = code;
	}

	/** Statuses that still need attention and count as open findings. */
	public boolean isActive() {
		return this == OPEN || this == TRIAGED;
	}

	public boolean canTransitionTo(FindingStatus target) {
		return allowedTargets().contains(target);
	}

	private Set<FindingStatus> allowedTargets() {
		return switch (this) {
			case OPEN -> Set.of(TRIAGED, FALSE_POSITIVE, ACCEPTED, FIXED);
			case TRIAGED -> Set.of(OPEN, FALSE_POSITIVE, ACCEPTED, FIXED);
			case FALSE_POSITIVE, ACCEPTED, FIXED -> Set.of(OPEN);
		};
	}

	@JsonValue
	public String code() {
		return code;
	}
}
