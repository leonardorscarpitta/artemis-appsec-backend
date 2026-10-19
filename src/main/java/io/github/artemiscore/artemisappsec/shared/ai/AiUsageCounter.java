package io.github.artemiscore.artemisappsec.shared.ai;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Daily AI attempts per organization and kind (UTC day). Attempts are paid for, so they are never given back. */
@Repository
public class AiUsageCounter {

	public static final String INSIGHT = "insight";
	public static final String REPORT = "report";

	private final JdbcClient jdbc;

	AiUsageCounter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Counts one attempt unless the limit is reached; a single statement, so concurrent requests cannot overshoot. */
	public boolean tryReserve(UUID organizationId, LocalDate day, String kind, int limit) {
		if (limit <= 0) {
			return false;
		}
		return jdbc.sql("""
				INSERT INTO organization_ai_usage (organization_id, day, kind, attempts) VALUES (:org, :day, :kind, 1)
				ON CONFLICT (organization_id, day, kind) DO UPDATE SET attempts = organization_ai_usage.attempts + 1
				WHERE organization_ai_usage.attempts < :limit
				""")
			.param("org", organizationId)
			.param("day", day)
			.param("kind", kind)
			.param("limit", limit)
			.update() == 1;
	}
}
