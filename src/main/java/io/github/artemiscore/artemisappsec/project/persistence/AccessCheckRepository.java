package io.github.artemiscore.artemisappsec.project.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.artemiscore.artemisappsec.project.domain.AccessCheck;
import io.github.artemiscore.artemisappsec.project.domain.AccessCheckStatus;

public interface AccessCheckRepository extends JpaRepository<AccessCheck, UUID> {

	/** Locks the oldest queued check so concurrent runners never claim the same one. */
	@Query(value = "SELECT * FROM repository_access_check WHERE status = 'QUEUED' ORDER BY requested_at LIMIT 1 FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	Optional<AccessCheck> lockNextQueued();

	Optional<AccessCheck> findFirstByProjectIdOrderByRequestedAtDesc(UUID projectId);

	Optional<AccessCheck> findFirstByProjectIdAndStatusIn(UUID projectId, Collection<AccessCheckStatus> statuses);

	List<AccessCheck> findByStatusAndStartedAtBefore(AccessCheckStatus status, Instant startedBefore);
}
