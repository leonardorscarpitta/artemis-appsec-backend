package io.github.artemiscore.artemis.dashboard.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import io.github.artemiscore.artemis.dashboard.service.DashboardSummary.SeverityCount;
import io.github.artemiscore.artemis.finding.service.FindingService;
import io.github.artemiscore.artemis.finding.service.FindingSummary;
import io.github.artemiscore.artemis.project.service.ProjectService;
import io.github.artemiscore.artemis.project.service.ProjectSummary;
import io.github.artemiscore.artemis.shared.domain.Severity;

@Service
class DashboardServiceImpl implements DashboardService {

	private static final int RECENT_LIMIT = 8;

	private final FindingService findings;
	private final ProjectService projects;

	DashboardServiceImpl(FindingService findings, ProjectService projects) {
		this.findings = findings;
		this.projects = projects;
	}

	@Override
	public DashboardSummary summary() {
		List<FindingSummary> active = findings.findActive();
		List<ProjectSummary> repositories = projects.findAll();
		double average = active.stream().mapToDouble(FindingSummary::cvss).average().orElse(0);
		List<SeverityCount> bySeverity = Arrays.stream(Severity.values())
			.map(s -> new SeverityCount(s, active.stream().filter(f -> f.severity() == s).count()))
			.toList();
		Instant lastScan = repositories.stream()
			.map(ProjectSummary::lastScanAt)
			.filter(Objects::nonNull)
			.max(Comparator.naturalOrder())
			.orElse(null);
		return new DashboardSummary(active.size(), countOf(bySeverity, Severity.CRITICAL), Math.round(average * 10) / 10.0,
				lastScan, repositories.size(), bySeverity, active.stream().limit(RECENT_LIMIT).toList());
	}

	private static long countOf(List<SeverityCount> counts, Severity severity) {
		return counts.stream().filter(c -> c.severity() == severity).mapToLong(SeverityCount::count).sum();
	}
}
