package io.github.artemiscore.artemisappsec.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	@EntityGraph(attributePaths = "pipeline")
	Optional<Project> findByOrganizationIdAndName(UUID organizationId, String name);

	List<Project> findByOrganizationIdOrderByNameAsc(UUID organizationId);

	boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
