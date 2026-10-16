package io.github.artemiscore.artemisappsec.organization.service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * A freshly generated ed25519 key pair in OpenSSH formats: the {@code authorized_keys} public line and the
 * {@code openssh-key-v1} private key file (unencrypted by OpenSSH; Artemis encrypts it at rest itself).
 */
record OpenSshKeyPair(String publicKey, byte[] privateKeyPem) {

	private static final String TYPE = "ssh-ed25519";
	// DER prefixes of the JDK's X.509 / PKCS#8 encodings of Ed25519 keys (RFC 8410), followed by the 32 raw bytes.
	private static final byte[] X509_PREFIX = { 0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00 };
	private static final byte[] PKCS8_PREFIX = { 0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70,
			0x04, 0x22, 0x04, 0x20 };
	private static final SecureRandom RANDOM = new SecureRandom();

	static OpenSshKeyPair generate(String comment) {
		KeyPair pair;
		try {
			pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Ed25519 is not available in this JVM", e);
		}
		byte[] publicRaw = raw(pair.getPublic().getEncoded(), X509_PREFIX);
		byte[] pkcs8 = pair.getPrivate().getEncoded();
		byte[] seed = raw(pkcs8, PKCS8_PREFIX);
		byte[] publicBlob = sshString(TYPE.getBytes(StandardCharsets.US_ASCII), publicRaw);
		try {
			String publicKey = TYPE + " " + Base64.getEncoder().encodeToString(publicBlob);
			return new OpenSshKeyPair(publicKey, privateKeyFile(publicBlob, publicRaw, seed, comment));
		}
		finally {
			Arrays.fill(seed, (byte) 0);
			Arrays.fill(pkcs8, (byte) 0);
		}
	}

	private static byte[] privateKeyFile(byte[] publicBlob, byte[] publicRaw, byte[] seed, String comment) {
		byte[] secret = new byte[64];
		System.arraycopy(seed, 0, secret, 0, 32);
		System.arraycopy(publicRaw, 0, secret, 32, 32);
		ByteArrayOutputStream section = new ByteArrayOutputStream();
		int check = RANDOM.nextInt();
		writeInt(section, check);
		writeInt(section, check);
		writeString(section, TYPE.getBytes(StandardCharsets.US_ASCII));
		writeString(section, publicRaw);
		writeString(section, secret);
		writeString(section, comment.getBytes(StandardCharsets.UTF_8));
		for (int pad = 1; section.size() % 8 != 0; pad++) {
			section.write(pad);
		}
		byte[] sectionBytes = section.toByteArray();

		ByteArrayOutputStream file = new ByteArrayOutputStream();
		file.writeBytes("openssh-key-v1\0".getBytes(StandardCharsets.US_ASCII));
		writeString(file, "none".getBytes(StandardCharsets.US_ASCII));
		writeString(file, "none".getBytes(StandardCharsets.US_ASCII));
		writeString(file, new byte[0]);
		writeInt(file, 1);
		writeString(file, publicBlob);
		writeString(file, sectionBytes);
		byte[] body = file.toByteArray();

		String base64 = Base64.getMimeEncoder(70, new byte[] { '\n' }).encodeToString(body);
		byte[] pem = ("-----BEGIN OPENSSH PRIVATE KEY-----\n" + base64 + "\n-----END OPENSSH PRIVATE KEY-----\n")
			.getBytes(StandardCharsets.US_ASCII);
		Arrays.fill(secret, (byte) 0);
		Arrays.fill(sectionBytes, (byte) 0);
		Arrays.fill(body, (byte) 0);
		return pem;
	}

	private static byte[] raw(byte[] der, byte[] prefix) {
		if (der.length != prefix.length + 32 || !Arrays.equals(Arrays.copyOf(der, prefix.length), prefix)) {
			throw new IllegalStateException("Unexpected Ed25519 key encoding");
		}
		return Arrays.copyOfRange(der, prefix.length, der.length);
	}

	private static byte[] sshString(byte[] first, byte[] second) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeString(out, first);
		writeString(out, second);
		return out.toByteArray();
	}

	private static void writeString(ByteArrayOutputStream out, byte[] value) {
		writeInt(out, value.length);
		out.writeBytes(value);
	}

	private static void writeInt(ByteArrayOutputStream out, int value) {
		out.writeBytes(ByteBuffer.allocate(4).putInt(value).array());
	}

	@Override
	public String toString() {
		return "OpenSshKeyPair[" + publicKey + "]";
	}
}
