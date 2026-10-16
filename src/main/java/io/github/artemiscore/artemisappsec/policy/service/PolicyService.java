package io.github.artemiscore.artemisappsec.policy.service;

import java.util.List;
import java.util.UUID;

public interface PolicyService {

	Policies get(UUID organizationId);

	Policies update(UUID organizationId, Policies policies);

	List<RiskAcceptanceView> findRiskAcceptances(UUID organizationId);

	RiskAcceptanceView acceptRisk(UUID organizationId, AcceptRiskCommand command, String approvedBy);

	void revokeRiskAcceptance(UUID organizationId, UUID id);
}
