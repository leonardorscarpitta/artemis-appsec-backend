package io.github.artemiscore.artemis.organization.service;

import java.util.List;

public interface IntegrationService {

	List<IntegrationView> findAll();

	IntegrationView connect(String id);

	IntegrationView disconnect(String id);

	IntegrationView configure(String id, String detail);
}
