package io.github.artemiscore.artemisappsec.organization.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** Personal access token for automation. Only a SHA-256 hash of the secret is stored. */
@Entity
@Table(name = "api_token")
public class ApiToken {

	@Id
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(nullable = false)
	private String name;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "last_four", nullable = false, length = 4)
	private String lastFour;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "api_token_scope", joinColumns = @JoinColumn(name = "token_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "scope")
	private Set<TokenScope> scopes = EnumSet.noneOf(TokenScope.class);

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	protected ApiToken() {
	}

	public ApiToken(UUID organizationId, String name, Set<TokenScope> scopes, String tokenHash, String lastFour,
			Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
		this.name = name;
		this.scopes.addAll(scopes);
		this.tokenHash = tokenHash;
		this.lastFour = lastFour;
		this.createdAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getLastFour() {
		return lastFour;
	}

	public Set<TokenScope> getScopes() {
		return Set.copyOf(scopes);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}
}
