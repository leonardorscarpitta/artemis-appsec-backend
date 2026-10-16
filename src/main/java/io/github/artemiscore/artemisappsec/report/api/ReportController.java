package io.github.artemiscore.artemisappsec.report.api;

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

import io.github.artemiscore.artemisappsec.organization.api.RequiresPermission;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.report.service.GenerateReportCommand;
import io.github.artemiscore.artemisappsec.report.service.ReportDetails;
import io.github.artemiscore.artemisappsec.report.service.ReportService;
import io.github.artemiscore.artemisappsec.report.service.ReportSummary;
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
	List<ReportSummary> list(Tenant tenant, Locale locale) {
		return reports.findAll(tenant.organizationId(), locale);
	}

	@GetMapping("/{id}")
	ReportDetails get(Tenant tenant, @PathVariable UUID id, Locale locale) {
		return reports.get(tenant.organizationId(), id, locale);
	}

	@PostMapping
	@RequiresPermission(Permission.TRIAGE)
	ResponseEntity<ReportSummary> request(Tenant tenant, @Valid @RequestBody GenerateReportCommand command, Locale locale) {
		ReportSummary created = reports.request(tenant.organizationId(), command, locale);
		return ResponseEntity.accepted().location(URI.create("/api/v1/reports/" + created.id())).body(created);
	}
}
