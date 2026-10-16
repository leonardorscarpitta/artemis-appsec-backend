package io.github.artemiscore.artemisappsec.finding.service;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;

/**
 * One issue reported by a scanner. Package fields are required for package-based kinds (vulnerability, license);
 * {@code locationPath} for the others.
 */
public record DetectedIssue(FindingKind kind, String ruleId, String title, String description, Severity severity,
		String cve, Double cvssScore, String cvssVector, String cwe, String packageName, String packageVersion,
		String fixedVersion, String ecosystem, String locationPath, Integer locationLine) {
}
