package io.github.artemiscore.artemisappsec.finding.persistence;

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

	List<Finding> findByStatusIn(Collection<FindingStatus> statuses);

	@EntityGraph(attributePaths = "events")
	List<Finding> findByProjectIdAndFingerprintIn(UUID projectId, Collection<String> fingerprints);

	@EntityGraph(attributePaths = "events")
	List<Finding> findByProjectIdAndStageAndStatusIn(UUID projectId, Stage stage, Collection<FindingStatus> statuses);

	@Query(value = "SELECT nextval('finding_reference_seq')", nativeQuery = true)
	long nextReferenceNumber();

	@Query("select f.projectId as projectId, count(f) as total from Finding f where f.status in :statuses group by f.projectId")
	List<ProjectCount> countByProjectAndStatusIn(Collection<FindingStatus> statuses);

	interface ProjectCount {
		UUID getProjectId();

		long getTotal();
	}
}
