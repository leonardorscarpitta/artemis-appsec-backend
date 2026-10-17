package io.github.artemiscore.artemisappsec.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	@EntityGraph(attributePaths = "pipeline")
	Optional<Project> findByOrganizationIdAndNameAndDeletedAtIsNull(UUID organizationId, String name);

	List<Project> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);

	/** Including removed repositories, whose history is still shown. */
	List<Project> findByOrganizationIdOrderByNameAsc(UUID organizationId);

	List<Project> findByOrganizationIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID organizationId);

	Optional<Project> findByIdAndOrganizationIdAndDeletedAtIsNotNull(UUID id, UUID organizationId);

	boolean existsByOrganizationIdAndNameAndDeletedAtIsNull(UUID organizationId, String name);
}
