package io.github.artemiscore.artemisappsec.policy.service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.finding.service.FindingPolicy;
import io.github.artemiscore.artemisappsec.policy.domain.LicenseDecision;
import io.github.artemiscore.artemisappsec.policy.domain.RemediationSla;
import io.github.artemiscore.artemisappsec.policy.domain.StageGate;
import io.github.artemiscore.artemisappsec.policy.persistence.LicenseRuleRepository;
import io.github.artemiscore.artemisappsec.policy.persistence.RemediationSlaRepository;
import io.github.artemiscore.artemisappsec.policy.persistence.StageGateRepository;
import io.github.artemiscore.artemisappsec.project.service.PipelinePolicy;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** Makes the stored policies effective for pipelines and findings (only repositories, so no module cycles). */
@Component
@Transactional(readOnly = true)
class PolicyRules implements PipelinePolicy, FindingPolicy {

	private final StageGateRepository gates;
	private final RemediationSlaRepository slas;
	private final LicenseRuleRepository licenses;

	PolicyRules(StageGateRepository gates, RemediationSlaRepository slas, LicenseRuleRepository licenses) {
		this.gates = gates;
		this.slas = slas;
		this.licenses = licenses;
	}

	@Override
	public Map<Stage, Gate> gates(UUID organizationId) {
		return gates.findByOrganizationId(organizationId).stream()
			.collect(Collectors.toMap(StageGate::getStage, g -> new Gate(g.isRequired(), g.getFailOn())));
	}

	@Override
	public Map<Severity, Integer> remediationDays(UUID organizationId) {
		return slas.findByOrganizationId(organizationId).stream()
			.collect(Collectors.toMap(RemediationSla::getSeverity, RemediationSla::getDays));
	}

	@Override
	public LicenseVerdict license(UUID organizationId, String licenseId) {
		String id = licenseId.toLowerCase(Locale.ROOT);
		return licenses.findByOrganizationIdOrderByLicenseIdAsc(organizationId).stream()
			.filter(rule -> rule.getLicenseId().toLowerCase(Locale.ROOT).equals(id))
			.findFirst()
			.map(rule -> rule.getDecision() == LicenseDecision.ALLOWED ? LicenseVerdict.ALLOW : LicenseVerdict.DENY)
			.orElse(LicenseVerdict.REVIEW);
	}
}
