package io.github.artemiscore.artemis.organization.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.organization.domain.DeployKey;

public interface DeployKeyRepository extends JpaRepository<DeployKey, UUID> {

	List<DeployKey> findAllByOrderByCreatedAtAsc();

	boolean existsByProjectIdAndFingerprint(UUID projectId, String fingerprint);
}
