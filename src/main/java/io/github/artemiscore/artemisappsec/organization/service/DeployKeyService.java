package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.UUID;

public interface DeployKeyService {

	List<DeployKeyView> findAll(UUID organizationId);

	void revoke(UUID organizationId, UUID id);
}
