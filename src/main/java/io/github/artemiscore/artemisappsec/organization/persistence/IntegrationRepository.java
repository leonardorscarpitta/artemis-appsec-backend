package io.github.artemiscore.artemisappsec.organization.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.organization.domain.Integration;

public interface IntegrationRepository extends JpaRepository<Integration, String> {

	List<Integration> findAllByOrderByPositionAsc();
}
