package io.github.artemiscore.artemisappsec.report.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface ReportService {

	List<ReportSummary> findAll(Locale locale);

	ReportDetails get(UUID id, Locale locale);

	/** Queues a report. It stays {@code generating} until a report generator is available. */
	ReportSummary request(GenerateReportCommand command, Locale locale);
}
