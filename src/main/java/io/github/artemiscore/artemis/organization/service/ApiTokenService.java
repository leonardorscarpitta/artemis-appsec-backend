package io.github.artemiscore.artemis.organization.service;

import java.util.List;
import java.util.UUID;

public interface ApiTokenService {

	List<ApiTokenView> findAll();

	CreatedApiToken create(CreateApiTokenCommand command);

	void revoke(UUID id);
}
