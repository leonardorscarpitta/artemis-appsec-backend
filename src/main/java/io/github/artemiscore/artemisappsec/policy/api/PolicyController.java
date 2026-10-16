package io.github.artemiscore.artemisappsec.policy.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	/** Recorded as approver when authentication is disabled (local development). */
	private static final String SYSTEM_USER = "system";

	private final PolicyService policies;

	PolicyController(PolicyService policies) {
		this.policies = policies;
	}

	@GetMapping("/policies")
	Policies get() {
		return policies.get();
	}

	@PutMapping("/policies")
	Policies update(@Valid @RequestBody Policies body) {
		return policies.update(body);
	}

	@GetMapping("/risk-acceptances")
	List<RiskAcceptanceView> riskAcceptances() {
		return policies.findRiskAcceptances();
	}

	@PostMapping("/risk-acceptances")
	ResponseEntity<RiskAcceptanceView> acceptRisk(@Valid @RequestBody AcceptRiskCommand command,
			Authentication authentication) {
		RiskAcceptanceView created = policies.acceptRisk(command, approver(authentication));
		return ResponseEntity.created(URI.create("/api/v1/risk-acceptances/" + created.id())).body(created);
	}

	@DeleteMapping("/risk-acceptances/{id}")
	ResponseEntity<Void> revoke(@PathVariable UUID id) {
		policies.revokeRiskAcceptance(id);
		return ResponseEntity.noContent().build();
	}

	private static String approver(Authentication authentication) {
		boolean authenticated = authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken);
		return authenticated ? authentication.getName() : SYSTEM_USER;
	}
}
