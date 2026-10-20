package io.github.artemiscore.artemisappsec.report.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.report.domain.ReportAuthor;
import io.github.artemiscore.artemisappsec.report.domain.ReportContent.Narrative;
import io.github.artemiscore.artemisappsec.report.domain.ReportSection;
import io.github.artemiscore.artemisappsec.report.domain.ReportStatus;
import io.github.artemiscore.artemisappsec.report.domain.ReportType;

/**
 * {@code scope} is the repository name, or {@code null} for the whole organization. {@code error} is a short code
 * ({@code generation-failed}, {@code too-many-attempts}) when the report failed; {@code narrative} says who wrote its
 * texts once it is ready.
 */
public record ReportSummary(UUID id, String title, ReportType type, Set<ReportSection> sections, String scope,
		LocalDate periodStart, LocalDate periodEnd, Instant generatedAt, ReportAuthor generatedBy, ReportStatus status,
		String error, Narrative narrative) {
}
