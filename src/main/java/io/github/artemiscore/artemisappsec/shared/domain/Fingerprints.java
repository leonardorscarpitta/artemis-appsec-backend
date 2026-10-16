package io.github.artemiscore.artemisappsec.shared.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** OpenSSH-style key fingerprints. */
public final class Fingerprints {

	private Fingerprints() {
	}

	/** {@code SHA256:<unpadded base64>} of an SSH public key blob, as printed by {@code ssh-keygen -lf}. */
	public static String sha256(byte[] keyBlob) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyBlob);
			return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
