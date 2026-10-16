package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.UUID;

public interface ApiTokenService {

	List<ApiTokenView> findAll(UUID organizationId);

	CreatedApiToken create(UUID organizationId, CreateApiTokenCommand command);

	void revoke(UUID organizationId, UUID id);
}
