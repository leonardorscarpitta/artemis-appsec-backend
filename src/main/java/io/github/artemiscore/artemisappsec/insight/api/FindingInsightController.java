package io.github.artemiscore.artemisappsec.insight.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.insight.service.FindingInsightService;
import io.github.artemiscore.artemisappsec.insight.service.FindingInsightView;
import io.github.artemiscore.artemisappsec.organization.api.RequiresPermission;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/findings/{id}/insight")
@Tag(name = "Findings", description = "Vulnerabilities detected in monitored repositories")
class FindingInsightController {

	private final FindingInsightService insights;

	FindingInsightController(FindingInsightService insights) {
		this.insights = insights;
	}

	@GetMapping
	@Operation(summary = "Cached AI explanation of a finding (404 when none was generated yet)")
	FindingInsightView get(Tenant tenant, @PathVariable UUID id) {
		return insights.find(tenant.organizationId(), id)
			.orElseThrow(() -> new ResourceNotFoundException("Insight for finding", id));
	}

	/** Triage permission: generating spends the organization's AI budget. */
	@PostMapping
	@RequiresPermission(Permission.TRIAGE)
	@Operation(summary = "Explain a finding with AI (cached: the same CVE and package version is never generated twice)")
	FindingInsightView generate(Tenant tenant, @PathVariable UUID id) {
		return insights.generate(tenant.organizationId(), id);
	}
}
