package io.github.artemiscore.artemis.report.service;

import io.github.artemiscore.artemis.report.domain.ReportPeriod;
import io.github.artemiscore.artemis.report.domain.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** {@code scope}: repository name, or {@code null}/blank for the whole organization. */
public record GenerateReportCommand(@NotNull ReportType type, @Pattern(regexp = "^[\\w.-]{0,255}$") String scope,
		@NotNull ReportPeriod period) {
}
