package io.github.artemiscore.artemis.finding.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemis.finding.domain.FindingStatus;
import io.github.artemiscore.artemis.finding.service.ChangeStatusCommand;
import io.github.artemiscore.artemis.finding.service.FindingDetails;
import io.github.artemiscore.artemis.finding.service.FindingQuery;
import io.github.artemiscore.artemis.finding.service.FindingService;
import io.github.artemiscore.artemis.finding.service.FindingSummary;
import io.github.artemiscore.artemis.shared.domain.Severity;
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
	List<FindingSummary> search(@RequestParam(required = false) @Size(max = 200) String q,
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
		return findings.search(new FindingQuery(q, severity, status, repository, field, ascending));
	}

	@GetMapping("/{id}")
	FindingDetails get(@PathVariable UUID id) {
		return findings.get(id);
	}

	@PostMapping("/{id}/status")
	FindingDetails changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusCommand command) {
		return findings.changeStatus(id, command);
	}
}
