package io.github.artemiscore.artemisappsec.policy.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.organization.service.OrganizationCreated;
import io.github.artemiscore.artemisappsec.policy.domain.RemediationSla;
import io.github.artemiscore.artemisappsec.policy.domain.StageGate;
import io.github.artemiscore.artemisappsec.policy.persistence.RemediationSlaRepository;
import io.github.artemiscore.artemisappsec.policy.persistence.StageGateRepository;
import io.github.artemiscore.artemisappsec.shared.domain.FailThreshold;
import io.github.artemiscore.artemisappsec.shared.domain.Severity;
import io.github.artemiscore.artemisappsec.shared.domain.Stage;

/** A new organization starts from the default policies (no stage required, no license rules). */
@Component
class PolicyProvisioning {

	private static final Map<Stage, FailThreshold> DEFAULT_GATES = Map.of(Stage.SECRETS, FailThreshold.LOW, Stage.SAST,
			FailThreshold.HIGH, Stage.SCA, FailThreshold.CRITICAL, Stage.LICENSE, FailThreshold.NEVER, Stage.DOCKERFILE,
			FailThreshold.HIGH, Stage.IMAGE, FailThreshold.CRITICAL, Stage.IAC, FailThreshold.HIGH, Stage.DAST,
			FailThreshold.HIGH);

	private static final Map<Severity, Integer> DEFAULT_SLA_DAYS = Map.of(Severity.CRITICAL, 7, Severity.HIGH, 30,
			Severity.MEDIUM, 90, Severity.LOW, 180);

	private final StageGateRepository gates;
	private final RemediationSlaRepository slas;

	PolicyProvisioning(StageGateRepository gates, RemediationSlaRepository slas) {
		this.gates = gates;
		this.slas = slas;
	}

	@EventListener
	void onOrganizationCreated(OrganizationCreated event) {
		gates.saveAll(List.of(Stage.values()).stream()
			.map(stage -> new StageGate(event.organizationId(), stage, DEFAULT_GATES.get(stage), false))
			.toList());
		slas.saveAll(DEFAULT_SLA_DAYS.entrySet().stream()
			.map(e -> new RemediationSla(event.organizationId(), e.getKey(), e.getValue()))
			.toList());
	}
}
