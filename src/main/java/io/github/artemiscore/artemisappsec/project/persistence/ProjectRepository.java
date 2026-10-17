package io.github.artemiscore.artemisappsec.project.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemisappsec.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	@EntityGraph(attributePaths = "pipeline")
	Optional<Project> findByOrganizationIdAndName(UUID organizationId, String name);

	List<Project> findByOrganizationIdOrderByNameAsc(UUID organizationId);

	boolean existsByOrganizationIdAndName(UUID organizationId, String name);

	/** Locks repositories whose timed schedule is due, skipping rows another API instance is already handling. */
	@Query(value = """
			SELECT id FROM project
			WHERE scan_schedule IN ('EVERY_6_HOURS', 'EVERY_24_HOURS', 'EVERY_7_DAYS')
			  AND (next_scan_at IS NULL OR next_scan_at <= :now)
			ORDER BY next_scan_at NULLS FIRST
			LIMIT :limit
			FOR UPDATE SKIP LOCKED""", nativeQuery = true)
	List<UUID> lockDueForScheduledScan(Instant now, int limit);
}
