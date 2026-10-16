package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only SSH deploy key of a repository. Keys Artemis generates ({@code managed}) also hold the private part,
 * encrypted by the service layer; older keys only have the public part and cannot be used to clone.
 */
@Entity
@Table(name = "deploy_key")
public class DeployKey {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Column(name = "key_type", nullable = false)
	private String keyType;

	@Column(name = "public_key", nullable = false)
	private String publicKey;

	@Column(nullable = false)
	private String fingerprint;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(nullable = false)
	private boolean managed;

	@Column(name = "private_key_ciphertext")
	private byte[] privateKeyCiphertext;

	@Column(name = "private_key_nonce")
	private byte[] privateKeyNonce;

	@Column(name = "encryption_key_id")
	private String encryptionKeyId;

	protected DeployKey() {
	}

	public DeployKey(String name, UUID projectId, SshPublicKey key) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.projectId = projectId;
		this.keyType = key.type();
		this.publicKey = key.encoded();
		this.fingerprint = key.fingerprint();
		this.createdAt = Instant.now();
	}

	/** A generated key; the private part is set right after with {@link #storeSecret}, using the id as AAD. */
	public static DeployKey managed(String name, UUID projectId, SshPublicKey key) {
		DeployKey deployKey = new DeployKey(name, projectId, key);
		deployKey.managed = true;
		return deployKey;
	}

	public void storeSecret(byte[] ciphertext, byte[] nonce, String keyId) {
		this.privateKeyCiphertext = ciphertext;
		this.privateKeyNonce = nonce;
		this.encryptionKeyId = keyId;
	}

	public void markUsed() {
		this.lastUsedAt = Instant.now();
	}

	public boolean isManaged() {
		return managed;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public byte[] getPrivateKeyCiphertext() {
		return privateKeyCiphertext;
	}

	public byte[] getPrivateKeyNonce() {
		return privateKeyNonce;
	}

	public String getEncryptionKeyId() {
		return encryptionKeyId;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getKeyType() {
		return keyType;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}
}
