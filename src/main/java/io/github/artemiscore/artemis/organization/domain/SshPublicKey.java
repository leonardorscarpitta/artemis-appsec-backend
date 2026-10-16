package io.github.artemiscore.artemis.organization.domain;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;

/** An OpenSSH public key in {@code authorized_keys} format, validated and fingerprinted. */
public record SshPublicKey(String type, String encoded, String fingerprint) {

	private static final Set<String> SUPPORTED_TYPES = Set.of("ssh-ed25519", "ssh-rsa", "ecdsa-sha2-nistp256",
			"ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521");
	private static final int MIN_RSA_BITS = 2048;

	public static SshPublicKey parse(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("A public key is required");
		}
		String key = raw.strip();
		if (key.contains("PRIVATE KEY") || key.startsWith("-----BEGIN")) {
			throw new IllegalArgumentException("This looks like a private key. Paste only the public key (the .pub file)");
		}
		if (key.contains("\n") || key.contains("\r")) {
			throw new IllegalArgumentException("The public key must be a single line");
		}
		String[] parts = key.split("\\s+", 3);
		if (parts.length < 2 || !SUPPORTED_TYPES.contains(parts[0])) {
			throw new IllegalArgumentException("Unsupported key. Use an ssh-ed25519, ssh-rsa or ecdsa public key");
		}
		byte[] blob;
		try {
			blob = Base64.getDecoder().decode(parts[1]);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("The public key is not valid base64");
		}
		validateBlob(parts[0], blob);
		return new SshPublicKey(parts[0], parts[0] + " " + parts[1], "SHA256:" + sha256Base64(blob));
	}

	private static void validateBlob(String type, byte[] blob) {
		try {
			ByteBuffer buffer = ByteBuffer.wrap(blob);
			String embeddedType = new String(readField(buffer), StandardCharsets.US_ASCII);
			if (!embeddedType.equals(type)) {
				throw new IllegalArgumentException("The key data does not match its declared type");
			}
			if (type.equals("ssh-rsa")) {
				readField(buffer); // public exponent
				int bits = new BigInteger(1, readField(buffer)).bitLength();
				if (bits < MIN_RSA_BITS) {
					throw new IllegalArgumentException("RSA keys must be at least %d bits".formatted(MIN_RSA_BITS));
				}
			}
			else if (type.equals("ssh-ed25519") && readField(buffer).length != 32) {
				throw new IllegalArgumentException("Invalid ed25519 key length");
			}
		}
		catch (BufferUnderflowException | NegativeArraySizeException ex) {
			throw new IllegalArgumentException("The public key data is truncated or corrupted");
		}
	}

	private static byte[] readField(ByteBuffer buffer) {
		int length = buffer.getInt();
		if (length < 0 || length > buffer.remaining()) {
			throw new BufferUnderflowException();
		}
		byte[] field = new byte[length];
		buffer.get(field);
		return field;
	}

	private static String sha256Base64(byte[] data) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
			return Base64.getEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
