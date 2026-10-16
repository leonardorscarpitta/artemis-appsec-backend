package io.github.artemiscore.artemis.finding.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemis.finding.domain.Finding;
import io.github.artemiscore.artemis.finding.domain.FindingStatus;

public interface FindingRepository extends JpaRepository<Finding, UUID>, JpaSpecificationExecutor<Finding> {

	@EntityGraph(attributePaths = "events")
	Optional<Finding> findWithEventsById(UUID id);

	List<Finding> findByStatusIn(Collection<FindingStatus> statuses);

	@Query("select f.projectId as projectId, count(f) as total from Finding f where f.status in :statuses group by f.projectId")
	List<ProjectCount> countByProjectAndStatusIn(Collection<FindingStatus> statuses);

	interface ProjectCount {
		UUID getProjectId();

		long getTotal();
	}
}
