package io.github.artemiscore.artemis.report.api;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemis.report.service.GenerateReportCommand;
import io.github.artemiscore.artemis.report.service.ReportDetails;
import io.github.artemiscore.artemis.report.service.ReportService;
import io.github.artemiscore.artemis.report.service.ReportSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Text fields are returned in the language of the {@code Accept-Language} header (en-US or pt-BR). */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Security summaries and compliance reports")
class ReportController {

	private final ReportService reports;

	ReportController(ReportService reports) {
		this.reports = reports;
	}

	@GetMapping
	List<ReportSummary> list(Locale locale) {
		return reports.findAll(locale);
	}

	@GetMapping("/{id}")
	ReportDetails get(@PathVariable UUID id, Locale locale) {
		return reports.get(id, locale);
	}

	@PostMapping
	ResponseEntity<ReportSummary> request(@Valid @RequestBody GenerateReportCommand command, Locale locale) {
		ReportSummary created = reports.request(command, locale);
		return ResponseEntity.accepted().location(URI.create("/api/v1/reports/" + created.id())).body(created);
	}
}
