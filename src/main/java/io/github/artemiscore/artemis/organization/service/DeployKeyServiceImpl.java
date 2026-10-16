package io.github.artemiscore.artemis.organization.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemis.organization.domain.DeployKey;
import io.github.artemiscore.artemis.organization.domain.SshPublicKey;
import io.github.artemiscore.artemis.organization.persistence.DeployKeyRepository;
import io.github.artemiscore.artemis.project.service.ProjectService;
import io.github.artemiscore.artemis.project.service.ProjectSummary;
import io.github.artemiscore.artemis.shared.error.BusinessRuleException;
import io.github.artemiscore.artemis.shared.error.ResourceNotFoundException;

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
	public List<DeployKeyView> findAll() {
		Map<UUID, String> names = projects.findAll().stream()
			.collect(Collectors.toMap(ProjectSummary::id, ProjectSummary::name));
		return keys.findAllByOrderByCreatedAtAsc().stream().map(k -> toView(k, names.get(k.getProjectId()))).toList();
	}

	@Override
	@Transactional
	public DeployKeyView add(AddDeployKeyCommand command) {
		SshPublicKey key = SshPublicKey.parse(command.publicKey());
		ProjectSummary project = projects.getByName(command.repository()).project();
		if (keys.existsByProjectIdAndFingerprint(project.id(), key.fingerprint())) {
			throw new BusinessRuleException("This key is already registered for '%s'".formatted(project.name()));
		}
		DeployKey saved = keys.save(new DeployKey(command.name().strip(), project.id(), key));
		return toView(saved, project.name());
	}

	@Override
	@Transactional
	public void revoke(UUID id) {
		if (!keys.existsById(id)) {
			throw new ResourceNotFoundException("Deploy key", id);
		}
		keys.deleteById(id);
	}

	private static DeployKeyView toView(DeployKey k, String repository) {
		return new DeployKeyView(k.getId(), k.getName(), k.getKeyType(), k.getFingerprint(), repository, k.getCreatedAt(),
				k.getLastUsedAt());
	}
}
