package io.github.artemiscore.artemisappsec.report.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.artemiscore.artemisappsec.report.domain.Report;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	List<Report> findAllByOrderByGeneratedAtDesc();
}
