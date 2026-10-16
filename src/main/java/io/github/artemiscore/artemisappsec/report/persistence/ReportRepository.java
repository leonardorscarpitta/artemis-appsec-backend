package io.github.artemiscore.artemisappsec.report.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.report.domain.Report;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	List<Report> findByOrganizationIdOrderByGeneratedAtDesc(UUID organizationId);

	Optional<Report> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
