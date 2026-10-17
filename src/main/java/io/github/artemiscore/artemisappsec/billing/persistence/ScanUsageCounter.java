package io.github.artemiscore.artemisappsec.billing.persistence;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Monthly scan counter per organization; {@code month} is the first day of the UTC month. */
@Repository
public class ScanUsageCounter {

	private final JdbcClient jdbc;

	public ScanUsageCounter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Counts one scan unless the limit is reached; a single statement, so concurrent requests cannot overshoot. */
	public boolean tryIncrement(UUID organizationId, LocalDate month, int limit) {
		if (limit <= 0) {
			return false;
		}
		return jdbc.sql("""
				INSERT INTO organization_scan_usage (organization_id, month, scans) VALUES (:org, :month, 1)
				ON CONFLICT (organization_id, month) DO UPDATE SET scans = organization_scan_usage.scans + 1
				WHERE organization_scan_usage.scans < :limit
				""")
			.param("org", organizationId)
			.param("month", month)
			.param("limit", limit)
			.update() == 1;
	}

	public void decrement(UUID organizationId, LocalDate month) {
		jdbc.sql("UPDATE organization_scan_usage SET scans = scans - 1 WHERE organization_id = :org AND month = :month AND scans > 0")
			.param("org", organizationId)
			.param("month", month)
			.update();
	}

	public long scans(UUID organizationId, LocalDate month) {
		return jdbc.sql("SELECT scans FROM organization_scan_usage WHERE organization_id = :org AND month = :month")
			.param("org", organizationId)
			.param("month", month)
			.query(Long.class)
			.optional()
			.orElse(0L);
	}
}
