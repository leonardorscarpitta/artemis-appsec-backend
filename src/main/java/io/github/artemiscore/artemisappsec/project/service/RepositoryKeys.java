package io.github.artemiscore.artemisappsec.project.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for the read-only deploy keys Artemis generates for private repositories, implemented by the organization
 * module (which owns deploy keys). The private part never leaves the implementation except through
 * {@link #releasePrivateKey}, for the scanner.
 */
public interface RepositoryKeys {

	/** Whether keys can be generated (an encryption key is configured). */
	boolean enabled();

	/** Generates a new key for the repository, replacing its previous Artemis-generated key. */
	RepositoryKey generate(UUID projectId, String repositoryName);

	Optional<RepositoryKey> find(UUID projectId);

	/** Decrypts the private key (OpenSSH format) for the scanner and records the use; {@code purpose} is logged. */
	Optional<String> releasePrivateKey(UUID projectId, String purpose);
}
