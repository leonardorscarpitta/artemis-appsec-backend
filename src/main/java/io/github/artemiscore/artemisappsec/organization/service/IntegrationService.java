package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.UUID;

public interface IntegrationService {

	List<IntegrationView> findAll(UUID organizationId);

	IntegrationView connect(UUID organizationId, String id);

	IntegrationView disconnect(UUID organizationId, String id);

	IntegrationView configure(UUID organizationId, String id, String detail);
}
