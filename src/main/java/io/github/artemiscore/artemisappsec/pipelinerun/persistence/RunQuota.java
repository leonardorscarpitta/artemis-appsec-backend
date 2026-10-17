package io.github.artemiscore.artemisappsec.pipelinerun.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The monthly scan a run reserved when it was queued. Settling and releasing are single conditional updates, so the
 * reservation is resolved exactly once even when the runner, a user and the stale sweep race.
 */
@Repository
public class RunQuota {

	private final JdbcClient jdbc;

	public RunQuota(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The run scanned something: its reservation becomes a used scan. */
	public void settle(UUID runId) {
		jdbc.sql("UPDATE pipeline_run SET quota_month = NULL WHERE id = :id AND quota_month IS NOT NULL")
			.param("id", runId)
			.update();
	}

	/** The run never scanned: returns the month to give the scan back to, if it was still reserved. */
	public Optional<LocalDate> release(UUID runId) {
		return jdbc.sql("""
				WITH reserved AS (
				    SELECT id, quota_month FROM pipeline_run WHERE id = :id AND quota_month IS NOT NULL FOR UPDATE
				)
				UPDATE pipeline_run r SET quota_month = NULL, quota_refunded = TRUE
				FROM reserved WHERE r.id = reserved.id
				RETURNING reserved.quota_month
				""")
			.param("id", runId)
			.query(LocalDate.class)
			.optional();
	}
}
