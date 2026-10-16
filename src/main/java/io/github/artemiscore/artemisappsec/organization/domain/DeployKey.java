package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only SSH key Artemis uses to clone a repository. Only the public part is ever stored. */
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
