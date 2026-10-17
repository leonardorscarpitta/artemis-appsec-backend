package io.github.artemiscore.artemisappsec.project.service;

/**
 * How Artemis reaches a repository. {@code type} is {@code public} (HTTPS, no credentials; other fields null) or
 * {@code ssh} (private, Artemis-generated deploy key). {@code hostKeyPinned} means the scanner ships the provider's
 * official host keys; otherwise {@code hostKeyFingerprint} is the key recorded on first contact (null until then).
 */
public record RepositoryAccess(String type, RepositoryKey deployKey, String host, String hostKeyFingerprint,
		boolean hostKeyPinned, AccessCheckView lastCheck) {

	static RepositoryAccess publicRepository() {
		return new RepositoryAccess("public", null, null, null, false, null);
	}
}
