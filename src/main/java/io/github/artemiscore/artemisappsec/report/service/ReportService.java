package io.github.artemiscore.artemisappsec.report.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface ReportService {

	List<ReportSummary> findAll(UUID organizationId, Locale locale);

	ReportDetails get(UUID organizationId, UUID id, Locale locale);

	/** Queues a report; it is generated in the background and becomes {@code ready} or {@code failed}. */
	ReportSummary request(UUID organizationId, GenerateReportCommand command, Locale locale);

	/** Queues a failed report again. */
	ReportSummary retry(UUID organizationId, UUID id, Locale locale);
}
