package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.Integration;
import io.github.artemiscore.artemisappsec.organization.persistence.IntegrationRepository;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class IntegrationServiceImpl implements IntegrationService {

	private final IntegrationRepository integrations;

	IntegrationServiceImpl(IntegrationRepository integrations) {
		this.integrations = integrations;
	}

	@Override
	public List<IntegrationView> findAll(UUID organizationId) {
		return integrations.findByOrganizationIdOrderByPositionAsc(organizationId).stream().map(IntegrationServiceImpl::toView).toList();
	}

	@Override
	@Transactional
	public IntegrationView connect(UUID organizationId, String id) {
		Integration integration = load(organizationId, id);
		integration.connect();
		return toView(integration);
	}

	@Override
	@Transactional
	public IntegrationView disconnect(UUID organizationId, String id) {
		Integration integration = load(organizationId, id);
		integration.disconnect();
		return toView(integration);
	}

	@Override
	@Transactional
	public IntegrationView configure(UUID organizationId, String id, String detail) {
		Integration integration = load(organizationId, id);
		integration.configure(detail);
		return toView(integration);
	}

	private Integration load(UUID organizationId, String id) {
		return integrations.findByOrganizationIdAndCode(organizationId, id).orElseThrow(() -> new ResourceNotFoundException("Integration", id));
	}

	private static IntegrationView toView(Integration i) {
		return new IntegrationView(i.getCode(), i.getName(), i.getShortCode(), i.getCategory(), i.getStatus(), i.getDetail(),
				i.isRemovable(), i.getLastSyncAt());
	}
}
