package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.organization.domain.DeployKey;

public interface DeployKeyRepository extends JpaRepository<DeployKey, UUID> {

	List<DeployKey> findByProjectIdInOrderByCreatedAtAsc(Collection<UUID> projectIds);

	Optional<DeployKey> findByProjectIdAndManagedTrue(UUID projectId);
}
