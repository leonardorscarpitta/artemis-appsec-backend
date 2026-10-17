package io.github.artemiscore.artemisappsec.policy.service;

import java.util.List;
import java.util.UUID;

public interface PolicyService {

	Policies get(UUID organizationId);

	Policies update(UUID organizationId, Policies policies);

	List<RiskAcceptanceView> findRiskAcceptances(UUID organizationId);

	/** Accepts the open finding(s) the command points to; each gets its own risk acceptance. */
	List<RiskAcceptanceView> acceptRisk(UUID organizationId, AcceptRiskCommand command, String approvedBy);

	/** Removes the acceptance and reopens its finding. */
	void revokeRiskAcceptance(UUID organizationId, UUID id, String actor);
}
