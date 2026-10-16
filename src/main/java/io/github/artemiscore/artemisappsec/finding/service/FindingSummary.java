package io.github.artemiscore.artemisappsec.finding.service;

import java.time.Instant;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.finding.domain.Ecosystem;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

public record FindingSummary(UUID id, String reference, String cve, String repository, String packageName,
		String packageGroup, String installedVersion, String fixedVersion, Ecosystem ecosystem, String title, double cvss,
		Severity severity, FindingStatus status, Stage stage, String tool, Instant detectedAt, Instant statusChangedAt) {
}
