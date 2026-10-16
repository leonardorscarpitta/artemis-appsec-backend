package io.github.artemiscore.artemisappsec.organization.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.artemiscore.artemisappsec.organization.domain.DeployKey;
import io.github.artemiscore.artemisappsec.organization.domain.SshPublicKey;
import io.github.artemiscore.artemisappsec.organization.persistence.DeployKeyRepository;
import io.github.artemiscore.artemisappsec.project.service.RepositoryKey;
import io.github.artemiscore.artemisappsec.project.service.RepositoryKeys;
import io.github.artemiscore.artemisappsec.shared.error.ApiProblemException;

/** Artemis-generated deploy keys of private repositories (one per repository), encrypted at rest. */
@Service
@Transactional(readOnly = true)
class ManagedDeployKeys implements RepositoryKeys {

	private static final Logger log = LoggerFactory.getLogger(ManagedDeployKeys.class);

	private final DeployKeyRepository keys;
	private final DeployKeyCipher cipher;

	ManagedDeployKeys(DeployKeyRepository keys, DeployKeyCipher cipher) {
		this.keys = keys;
		this.cipher = cipher;
	}

	@Override
	public boolean enabled() {
		return cipher.enabled();
	}

	@Override
	@Transactional
	public RepositoryKey generate(UUID projectId, String repositoryName) {
		if (!cipher.enabled()) {
			throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "deploy-keys-disabled",
					"Private repositories are not configured on this server");
		}
		keys.findByProjectIdAndManagedTrue(projectId).ifPresent(previous -> {
			keys.delete(previous);
			keys.flush();
		});
		OpenSshKeyPair pair = OpenSshKeyPair.generate("artemisappsec-" + repositoryName);
		byte[] pem = pair.privateKeyPem();
		try {
			DeployKey key = DeployKey.managed("Artemis · " + repositoryName, projectId, SshPublicKey.parse(pair.publicKey()));
			DeployKeyCipher.Sealed sealed = cipher.encrypt(key.getId(), pem);
			key.storeSecret(sealed.ciphertext(), sealed.nonce(), sealed.keyId());
			DeployKey saved = keys.save(key);
			log.info("Generated deploy key {} for repository '{}'", saved.getFingerprint(), repositoryName);
			return toView(saved);
		}
		finally {
			Arrays.fill(pem, (byte) 0);
		}
	}

	@Override
	public Optional<RepositoryKey> find(UUID projectId) {
		return keys.findByProjectIdAndManagedTrue(projectId).map(ManagedDeployKeys::toView);
	}

	@Override
	@Transactional
	public Optional<String> releasePrivateKey(UUID projectId, String purpose) {
		return keys.findByProjectIdAndManagedTrue(projectId).map(key -> {
			byte[] plain = cipher.decrypt(key.getId(),
					new DeployKeyCipher.Sealed(key.getPrivateKeyCiphertext(), key.getPrivateKeyNonce(), key.getEncryptionKeyId()));
			try {
				key.markUsed();
				log.info("Released deploy key {} to the scanner for {}", key.getFingerprint(), purpose);
				return new String(plain, StandardCharsets.US_ASCII);
			}
			finally {
				Arrays.fill(plain, (byte) 0);
			}
		});
	}

	private static RepositoryKey toView(DeployKey k) {
		return new RepositoryKey(k.getId(), k.getPublicKey(), k.getFingerprint(), k.getCreatedAt(), k.getLastUsedAt());
	}
}
