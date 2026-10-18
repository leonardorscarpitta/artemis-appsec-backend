package io.github.artemiscore.artemisappsec.report.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemisappsec.report.domain.Report;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	List<Report> findByOrganizationIdOrderByGeneratedAtDesc(UUID organizationId);

	Optional<Report> findByIdAndOrganizationId(UUID id, UUID organizationId);

	/** Locks the oldest report waiting for generation that no other instance is working on. */
	@Query(value = """
			SELECT * FROM report
			WHERE status = 'GENERATING' AND (started_at IS NULL OR started_at < :stuckBefore)
			ORDER BY generated_at LIMIT 1 FOR UPDATE SKIP LOCKED""", nativeQuery = true)
	Optional<Report> lockNextToGenerate(Instant stuckBefore);

	long countByOrganizationIdAndAiUsedTrueAndGeneratedAtGreaterThanEqual(UUID organizationId, Instant since);
}
