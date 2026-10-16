package io.github.artemiscore.artemis.organization.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemis.organization.domain.Integration;
import io.github.artemiscore.artemis.organization.persistence.IntegrationRepository;
import io.github.artemiscore.artemis.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class IntegrationServiceImpl implements IntegrationService {

	private final IntegrationRepository integrations;

	IntegrationServiceImpl(IntegrationRepository integrations) {
		this.integrations = integrations;
	}

	@Override
	public List<IntegrationView> findAll() {
		return integrations.findAllByOrderByPositionAsc().stream().map(IntegrationServiceImpl::toView).toList();
	}

	@Override
	@Transactional
	public IntegrationView connect(String id) {
		Integration integration = load(id);
		integration.connect();
		return toView(integration);
	}

	@Override
	@Transactional
	public IntegrationView disconnect(String id) {
		Integration integration = load(id);
		integration.disconnect();
		return toView(integration);
	}

	@Override
	@Transactional
	public IntegrationView configure(String id, String detail) {
		Integration integration = load(id);
		integration.configure(detail);
		return toView(integration);
	}

	private Integration load(String id) {
		return integrations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Integration", id));
	}

	private static IntegrationView toView(Integration i) {
		return new IntegrationView(i.getId(), i.getName(), i.getShortCode(), i.getCategory(), i.getStatus(), i.getDetail(),
				i.isRemovable(), i.getLastSyncAt());
	}
}
