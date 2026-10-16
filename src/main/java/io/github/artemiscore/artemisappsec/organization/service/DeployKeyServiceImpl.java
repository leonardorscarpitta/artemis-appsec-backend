package io.github.artemiscore.artemisappsec.organization.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.DeployKey;
import io.github.artemiscore.artemisappsec.organization.domain.SshPublicKey;
import io.github.artemiscore.artemisappsec.organization.persistence.DeployKeyRepository;
import io.github.artemiscore.artemisappsec.project.service.ProjectService;
import io.github.artemiscore.artemisappsec.project.service.ProjectSummary;
import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import io.github.artemiscore.artemisappsec.shared.error.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
class DeployKeyServiceImpl implements DeployKeyService {

	private final DeployKeyRepository keys;
	private final ProjectService projects;

	DeployKeyServiceImpl(DeployKeyRepository keys, ProjectService projects) {
		this.keys = keys;
		this.projects = projects;
	}

	@Override
	public List<DeployKeyView> findAll(UUID organizationId) {
		Map<UUID, String> names = projectNames(organizationId);
		return keys.findByProjectIdInOrderByCreatedAtAsc(names.keySet()).stream().map(k -> toView(k, names.get(k.getProjectId()))).toList();
	}

	@Override
	@Transactional
	public DeployKeyView add(UUID organizationId, AddDeployKeyCommand command) {
		SshPublicKey key = SshPublicKey.parse(command.publicKey());
		ProjectSummary project = projects.getByName(organizationId, command.repository()).project();
		if (keys.existsByProjectIdAndFingerprint(project.id(), key.fingerprint())) {
			throw new BusinessRuleException("This key is already registered for '%s'".formatted(project.name()));
		}
		DeployKey saved = keys.save(new DeployKey(command.name().strip(), project.id(), key));
		return toView(saved, project.name());
	}

	@Override
	@Transactional
	public void revoke(UUID organizationId, UUID id) {
		DeployKey key = keys.findById(id)
			.filter(k -> projectNames(organizationId).containsKey(k.getProjectId()))
			.orElseThrow(() -> new ResourceNotFoundException("Deploy key", id));
		keys.delete(key);
	}

	private Map<UUID, String> projectNames(UUID organizationId) {
		return projects.findAll(organizationId).stream().collect(Collectors.toMap(ProjectSummary::id, ProjectSummary::name));
	}

	private static DeployKeyView toView(DeployKey k, String repository) {
		return new DeployKeyView(k.getId(), k.getName(), k.getKeyType(), k.getFingerprint(), repository, k.getCreatedAt(),
				k.getLastUsedAt());
	}
}
