package io.github.artemiscore.artemisappsec.project.domain;

import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/** How a repository is scanned besides Run scan. Timed schedules (6h, 24h, 7d) are a PRO feature. */
public enum ScanSchedule {
	MANUAL("manual", null), ON_PUSH("push", null), EVERY_6_HOURS("6h", Duration.ofHours(6)),
	EVERY_24_HOURS("24h", Duration.ofHours(24)), EVERY_7_DAYS("7d", Duration.ofDays(7));

	private final String code;
	private final Duration interval;

	ScanSchedule(String code, Duration interval) {
		this.code = code;
		this.interval = interval;
	}

	@JsonValue
	public String code() {
		return code;
	}

	/** Empty unless the scheduler queues runs for this schedule. */
	public Optional<Duration> interval() {
		return Optional.ofNullable(interval);
	}
}
