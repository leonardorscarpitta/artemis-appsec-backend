package io.github.artemiscore.artemisappsec.report.service;

import java.util.Set;

import io.github.artemiscore.artemisappsec.report.domain.ReportPeriod;
import io.github.artemiscore.artemisappsec.report.domain.ReportSection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code scope}: repository name, or {@code null}/blank for all of the organization's repositories. {@code sections}:
 * optional topics to include (null = executive summary and recommendations).
 */
public record GenerateReportCommand(Set<ReportSection> sections, @Pattern(regexp = "^[\\w.-]{0,255}$") String scope,
		@NotNull ReportPeriod period) {
}
