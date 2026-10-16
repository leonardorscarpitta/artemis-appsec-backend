package io.github.artemiscore.artemis.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	@EntityGraph(attributePaths = "pipeline")
	Optional<Project> findByName(String name);

	List<Project> findAllByOrderByNameAsc();

	boolean existsByName(String name);
}
