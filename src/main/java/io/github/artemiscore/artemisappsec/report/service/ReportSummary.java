package io.github.artemiscore.artemisappsec.report.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.report.domain.ReportAuthor;
import io.github.artemiscore.artemisappsec.report.domain.ReportStatus;
import io.github.artemiscore.artemisappsec.report.domain.ReportType;

/** {@code scope} is the repository name, or {@code null} for the whole organization. */
public record ReportSummary(UUID id, String title, ReportType type, String scope, LocalDate periodStart,
		LocalDate periodEnd, Instant generatedAt, ReportAuthor generatedBy, ReportStatus status) {
}
