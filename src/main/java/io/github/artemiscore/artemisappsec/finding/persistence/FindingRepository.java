package io.github.artemiscore.artemisappsec.finding.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemisappsec.finding.domain.Finding;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

public interface FindingRepository extends JpaRepository<Finding, UUID>, JpaSpecificationExecutor<Finding> {

	@EntityGraph(attributePaths = "events")
	Optional<Finding> findWithEventsById(UUID id);

	List<Finding> findByProjectIdInAndStatusIn(Collection<UUID> projectIds, Collection<FindingStatus> statuses);

	@EntityGraph(attributePaths = "events")
	List<Finding> findByProjectIdAndFingerprintIn(UUID projectId, Collection<String> fingerprints);

	@EntityGraph(attributePaths = "events")
	List<Finding> findByProjectIdAndStageAndStatusIn(UUID projectId, Stage stage, Collection<FindingStatus> statuses);

	@Query(value = "SELECT nextval('finding_reference_seq')", nativeQuery = true)
	long nextReferenceNumber();

	@Query("select f.projectId as projectId, count(f) as total from Finding f where f.projectId in :projectIds and f.status in :statuses group by f.projectId")
	List<ProjectCount> countByProjectAndStatusIn(Collection<UUID> projectIds, Collection<FindingStatus> statuses);

	/** Rows of [day (yyyy-MM-dd), severity, open findings at the end of that day in {@code zone}]. */
	@Query(value = """
			WITH days AS (
			    SELECT to_char(d, 'YYYY-MM-DD') AS day, (d + INTERVAL '1 day') AT TIME ZONE :zone AS day_end
			    FROM generate_series(CAST(CAST(:from AS date) AS timestamp), CAST(CAST(:to AS date) AS timestamp), INTERVAL '1 day') d
			), spans AS (
			    SELECT f.severity, e.status, e.occurred_at AS since,
			           lead(e.occurred_at) OVER (PARTITION BY e.finding_id ORDER BY e.occurred_at, e.id) AS until
			    FROM finding f JOIN finding_event e ON e.finding_id = f.id
			    WHERE f.project_id IN (:projectIds)
			)
			SELECT days.day, s.severity, count(*)
			FROM days JOIN spans s ON s.since < days.day_end AND (s.until IS NULL OR s.until >= days.day_end)
			WHERE s.status IN ('OPEN', 'TRIAGED')
			GROUP BY days.day, s.severity
			""", nativeQuery = true)
	List<Object[]> dailyOpenBySeverity(Collection<UUID> projectIds, LocalDate from, LocalDate to, String zone);

	/** Rows of [day (yyyy-MM-dd), first detections, transitions to fixed] for days in {@code zone} since {@code since}. */
	@Query(value = """
			SELECT to_char(e.occurred_at AT TIME ZONE :zone, 'YYYY-MM-DD') AS day,
			       count(*) FILTER (WHERE e.type = 'DETECTED'),
			       count(*) FILTER (WHERE e.type = 'STATUS_CHANGED' AND e.status = 'FIXED')
			FROM finding f JOIN finding_event e ON e.finding_id = f.id
			WHERE f.project_id IN (:projectIds) AND e.occurred_at >= :since
			GROUP BY 1
			""", nativeQuery = true)
	List<Object[]> dailyDetectedAndFixed(Collection<UUID> projectIds, Instant since, String zone);

	/** One row: [fixes since {@code since}, mean seconds from first detection to the fix]. */
	@Query(value = """
			SELECT count(*), extract(EPOCH FROM avg(e.occurred_at - f.detected_at))
			FROM finding f JOIN finding_event e ON e.finding_id = f.id
			WHERE f.project_id IN (:projectIds) AND e.occurred_at >= :since
			  AND e.type = 'STATUS_CHANGED' AND e.status = 'FIXED'
			""", nativeQuery = true)
	List<Object[]> remediationSince(Collection<UUID> projectIds, Instant since);

	interface ProjectCount {
		UUID getProjectId();

		long getTotal();
	}
}
