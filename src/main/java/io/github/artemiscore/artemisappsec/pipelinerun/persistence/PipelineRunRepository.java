package io.github.artemiscore.artemisappsec.pipelinerun.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemisappsec.pipelinerun.domain.PipelineRun;
import io.github.artemiscore.artemisappsec.pipelinerun.domain.RunStatus;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID>, JpaSpecificationExecutor<PipelineRun> {


	@EntityGraph(attributePaths = "stages")
	Optional<PipelineRun> findByNumber(long number);

	@EntityGraph(attributePaths = "stages")
	Optional<PipelineRun> findWithStagesById(UUID id);

	@EntityGraph(attributePaths = "stages")
	List<PipelineRun> findByProjectIdInAndStartedAtAfter(Collection<UUID> projectIds, Instant since);

	@EntityGraph(attributePaths = "stages")
	List<PipelineRun> findByProjectIdAndStatusIn(UUID projectId, Collection<RunStatus> statuses);

	List<PipelineRun> findByStatusAndStartedAtBefore(RunStatus status, Instant before);

	boolean existsByProjectIdAndStatusIn(UUID projectId, Collection<RunStatus> statuses);

	/** Locks the oldest queued run so concurrent runners never claim the same one. */
	@Query(value = "SELECT id FROM pipeline_run WHERE status = 'QUEUED' ORDER BY started_at, number LIMIT 1 FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	Optional<UUID> lockNextQueued();

	/** Rows of [day (yyyy-MM-dd) in {@code zone}, status, runs] for runs started since {@code since}. */
	@Query(value = """
			SELECT to_char(r.started_at AT TIME ZONE :zone, 'YYYY-MM-DD') AS day, r.status, count(*)
			FROM pipeline_run r
			WHERE r.project_id IN (:projectIds) AND r.started_at >= :since AND r.status IN ('SUCCESS', 'FAILED', 'CANCELLED')
			GROUP BY 1, 2
			""", nativeQuery = true)
	List<Object[]> dailyStatusCounts(Collection<UUID> projectIds, Instant since, String zone);

	@Query(value = "SELECT nextval('pipeline_run_number_seq')", nativeQuery = true)
	long nextNumber();
}
