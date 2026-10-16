package io.github.artemiscore.artemis.organization.service;

import java.util.List;
import java.util.UUID;

public interface DeployKeyService {

	List<DeployKeyView> findAll();

	DeployKeyView add(AddDeployKeyCommand command);

	void revoke(UUID id);
}
