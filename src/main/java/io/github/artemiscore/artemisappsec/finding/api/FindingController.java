package io.github.artemiscore.artemisappsec.finding.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.finding.domain.FindingKind;
import io.github.artemiscore.artemisappsec.finding.domain.FindingStatus;
import io.github.artemiscore.artemisappsec.finding.service.ChangeStatusCommand;
import io.github.artemiscore.artemisappsec.finding.service.FindingDetails;
import io.github.artemiscore.artemisappsec.finding.service.FindingQuery;
import io.github.artemiscore.artemisappsec.finding.service.FindingService;
import io.github.artemiscore.artemisappsec.finding.service.FindingSummary;
import io.github.artemiscore.artemisappsec.organization.api.RequiresPermission;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/findings")
@Tag(name = "Findings", description = "Vulnerabilities detected in monitored repositories")
class FindingController {

	private final FindingService findings;

	FindingController(FindingService findings) {
		this.findings = findings;
	}

	@GetMapping
	List<FindingSummary> search(Tenant tenant, @RequestParam(required = false) @Size(max = 200) String q,
			@RequestParam(required = false) FindingKind kind,
			@RequestParam(required = false) Severity severity, @RequestParam(required = false) FindingStatus status,
			@RequestParam(required = false) @Size(max = 255) String repository,
			@RequestParam(defaultValue = "detected") String sort, @RequestParam(defaultValue = "desc") String dir) {
		FindingQuery.SortField field = switch (sort) {
			case "cvss" -> FindingQuery.SortField.CVSS;
			case "detected" -> FindingQuery.SortField.DETECTED;
			default -> throw new IllegalArgumentException("sort must be 'cvss' or 'detected'");
		};
		boolean ascending = switch (dir) {
			case "asc" -> true;
			case "desc" -> false;
			default -> throw new IllegalArgumentException("dir must be 'asc' or 'desc'");
		};
		return findings.search(tenant.organizationId(), new FindingQuery(q, kind, severity, status, repository, field, ascending));
	}

	/** Same filters as the list; downloads every matching finding as CSV. */
	@GetMapping(value = "/export", produces = "text/csv")
	ResponseEntity<String> export(Tenant tenant, @RequestParam(required = false) @Size(max = 200) String q,
			@RequestParam(required = false) FindingKind kind,
			@RequestParam(required = false) Severity severity, @RequestParam(required = false) FindingStatus status,
			@RequestParam(required = false) @Size(max = 255) String repository) {
		List<FindingSummary> rows = findings.search(tenant.organizationId(),
				new FindingQuery(q, kind, severity, status, repository, FindingQuery.SortField.DETECTED, false));
		String filename = "artemis-findings-%s-%s.csv".formatted(tenant.slug(), LocalDate.now(ZoneOffset.UTC));
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
			.body(FindingCsv.write(rows));
	}

	@GetMapping("/{id}")
	FindingDetails get(Tenant tenant, @PathVariable UUID id) {
		return findings.get(tenant.organizationId(), id);
	}

	@PostMapping("/{id}/status")
	@RequiresPermission(Permission.TRIAGE)
	FindingDetails changeStatus(Tenant tenant, @PathVariable UUID id, @Valid @RequestBody ChangeStatusCommand command) {
		return findings.changeStatus(tenant.organizationId(), id, command, tenant.actor());
	}
}
