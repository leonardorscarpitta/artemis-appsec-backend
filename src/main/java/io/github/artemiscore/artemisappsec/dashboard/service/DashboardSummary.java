package io.github.artemiscore.artemisappsec.dashboard.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

/**
 * {@code averageCvss} covers only findings that have a CVSS score (known vulnerabilities); {@code overdueFindings} are
 * open findings past their remediation SLA; {@code topRepositories} are the repositories with the most open findings.
 */
public record DashboardSummary(long openFindings, long criticalFindings, double averageCvss, Instant lastScanAt,
		long repositories, List<SeverityCount> bySeverity, List<FindingSummary> recent, long overdueFindings,
		List<KindCount> byKind, List<RepositoryCount> topRepositories) {

	public record SeverityCount(Severity severity, long count) {
	}

	public record KindCount(FindingKind kind, long count) {
	}

	public record RepositoryCount(String repository, long total, long critical, long high, long medium, long low) {
	}
}
