package io.github.artemiscore.artemisappsec.policy.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.artemiscore.artemisappsec.organization.api.RequiresPermission;
import io.github.artemiscore.artemisappsec.organization.domain.Permission;
import io.github.artemiscore.artemisappsec.organization.service.Tenant;
import io.github.artemiscore.artemisappsec.policy.service.AcceptRiskCommand;
import io.github.artemiscore.artemisappsec.policy.service.Policies;
import io.github.artemiscore.artemisappsec.policy.service.PolicyService;
import io.github.artemiscore.artemisappsec.policy.service.RiskAcceptanceView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Policies", description = "Organization-wide pipeline gates, remediation SLAs, license policy and risk acceptances")
class PolicyController {

	private final PolicyService policies;

	PolicyController(PolicyService policies) {
		this.policies = policies;
	}

	@GetMapping("/policies")
	Policies get(Tenant tenant) {
		return policies.get(tenant.organizationId());
	}

	@PutMapping("/policies")
	@RequiresPermission(Permission.PIPELINES)
	Policies update(Tenant tenant, @Valid @RequestBody Policies body) {
		return policies.update(tenant.organizationId(), body);
	}

	@GetMapping("/risk-acceptances")
	List<RiskAcceptanceView> riskAcceptances(Tenant tenant) {
		return policies.findRiskAcceptances(tenant.organizationId());
	}

	@PostMapping("/risk-acceptances")
	@RequiresPermission(Permission.TRIAGE)
	ResponseEntity<RiskAcceptanceView> acceptRisk(Tenant tenant, @Valid @RequestBody AcceptRiskCommand command) {
		RiskAcceptanceView created = policies.acceptRisk(tenant.organizationId(), command, tenant.actor());
		return ResponseEntity.created(URI.create("/api/v1/risk-acceptances/" + created.id())).body(created);
	}

	@DeleteMapping("/risk-acceptances/{id}")
	@RequiresPermission(Permission.TRIAGE)
	ResponseEntity<Void> revoke(Tenant tenant, @PathVariable UUID id) {
		policies.revokeRiskAcceptance(tenant.organizationId(), id);
		return ResponseEntity.noContent().build();
	}
}
