package io.github.artemiscore.artemis.organization.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemis.organization.domain.Integration;

public interface IntegrationRepository extends JpaRepository<Integration, String> {

	List<Integration> findAllByOrderByPositionAsc();
}
