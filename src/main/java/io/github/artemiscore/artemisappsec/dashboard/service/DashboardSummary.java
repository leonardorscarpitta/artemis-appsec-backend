package io.github.artemiscore.artemisappsec.dashboard.service;

import java.time.Instant;
import java.util.List;

import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

/**
 * {@code averageCvss} covers only findings that have a CVSS score (known vulnerabilities); {@code overdueFindings} are
 * open findings past their remediation SLA.
 */
public record DashboardSummary(long openFindings, long criticalFindings, double averageCvss, Instant lastScanAt,
		long repositories, List<SeverityCount> bySeverity, List<FindingSummary> recent, long overdueFindings) {

	public record SeverityCount(Severity severity, long count) {
	}
}
