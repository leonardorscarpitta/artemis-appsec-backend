package io.github.artemiscore.artemis.policy.service;

import java.util.List;
import java.util.UUID;

public interface PolicyService {

	Policies get();

	Policies update(Policies policies);

	List<RiskAcceptanceView> findRiskAcceptances();

	RiskAcceptanceView acceptRisk(AcceptRiskCommand command, String approvedBy);

	void revokeRiskAcceptance(UUID id);
}
