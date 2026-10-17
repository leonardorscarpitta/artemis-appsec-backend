package io.github.artemiscore.artemisappsec.finding.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.finding.domain.Ecosystem;
import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/**
 * {@code cve}, {@code cvss}, package fields and {@code ecosystem} are null for kinds that are not package based;
 * {@code location} is null for package-based kinds. {@code repositoryRemoved}: the repository was removed and the
 * finding is kept as history.
 */
public record FindingSummary(UUID id, String reference, FindingKind kind, String ruleId, String cve, String repository,
		boolean repositoryRemoved,
		String packageName, String packageGroup, String installedVersion, String fixedVersion, Ecosystem ecosystem,
		String title, Double cvss, Severity severity, FindingStatus status, Stage stage, String tool,
		Location location, Instant detectedAt, Instant statusChangedAt, Instant dueAt, boolean overdue) {

	public record Location(String path, Integer line) {
	}
}
