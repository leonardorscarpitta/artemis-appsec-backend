package io.github.artemiscore.artemisappsec.organization.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.artemiscore.artemisappsec.shared.config.ArtemisAppSecProperties;

/**
 * AES-256-GCM encryption of private deploy keys with the server's master key. The additional authenticated data binds
 * each ciphertext to its deploy key row and key id, so ciphertexts cannot be swapped between rows.
 */
@Component
class DeployKeyCipher {

	record Sealed(byte[] ciphertext, byte[] nonce, String keyId) {
	}

	private static final Logger log = LoggerFactory.getLogger(DeployKeyCipher.class);
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final String keyId;
	private final SecureRandom random = new SecureRandom();

	DeployKeyCipher(ArtemisAppSecProperties properties) {
		ArtemisAppSecProperties.DeployKeys config = properties.deployKeys();
		this.keyId = config == null ? "v1" : config.encryptionKeyId();
		this.key = parse(config == null ? "" : config.encryptionKey());
		if (key == null) {
			log.warn("DEPLOY_KEY_ENCRYPTION_KEY is not set to 32 base64-encoded bytes: private repositories are disabled");
		}
	}

	boolean enabled() {
		return key != null;
	}

	Sealed encrypt(UUID deployKeyId, byte[] plaintext) {
		byte[] nonce = new byte[NONCE_BYTES];
		random.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(aad(deployKeyId, keyId));
			return new Sealed(cipher.doFinal(plaintext), nonce, keyId);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not encrypt the deploy key", e);
		}
	}

	byte[] decrypt(UUID deployKeyId, Sealed sealed) {
		if (!keyId.equals(sealed.keyId())) {
			throw new IllegalStateException("Deploy key " + deployKeyId + " was encrypted with key '" + sealed.keyId()
					+ "', but the server has '" + keyId + "'");
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, sealed.nonce()));
			cipher.updateAAD(aad(deployKeyId, sealed.keyId()));
			return cipher.doFinal(sealed.ciphertext());
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Could not decrypt deploy key " + deployKeyId + " (wrong master key?)");
		}
	}

	private SecretKey requireKey() {
		if (key == null) {
			throw new IllegalStateException("Deploy key encryption is not configured");
		}
		return key;
	}

	private static byte[] aad(UUID deployKeyId, String keyId) {
		return ("artemisappsec-deploy-key:" + deployKeyId + ":" + keyId).getBytes(StandardCharsets.UTF_8);
	}

	private static SecretKey parse(String base64) {
		if (base64 == null || base64.isBlank()) {
			return null;
		}
		try {
			byte[] bytes = Base64.getDecoder().decode(base64);
			return bytes.length == 32 ? new SecretKeySpec(bytes, "AES") : null;
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}
}
