package io.github.artemiscore.artemisappsec.organization.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import io.github.artemiscore.artemisappsec.shared.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "invitation")
public class Invitation {

	public static final Duration VALIDITY = Duration.ofDays(7);
	private static final SecureRandom RANDOM = new SecureRandom();

	@Id
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "invited_by")
	private String invitedBy;

	@Column(name = "token_hash")
	private String tokenHash;

	/** The raw link token, only right after it was issued (never stored). */
	@Transient
	private String issuedToken;

	protected Invitation() {
	}

	public Invitation(UUID organizationId, String email, Role role, String invitedBy) {
		if (role == Role.OWNER) {
			throw new BusinessRuleException("The owner role cannot be assigned through an invitation");
		}
		this.id = UUID.randomUUID();
		this.organizationId = organizationId;
		this.email = email;
		this.role = role;
		this.invitedBy = invitedBy;
		resend();
	}

	/** Renews the validity and issues a new one-time link token, invalidating the previous one. */
	public void resend() {
		sentAt = Instant.now();
		expiresAt = sentAt.plus(VALIDITY);
		byte[] random = new byte[32];
		RANDOM.nextBytes(random);
		issuedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		tokenHash = hashToken(issuedToken);
	}

	public static String hashToken(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public String getIssuedToken() {
		return issuedToken;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Role getRole() {
		return role;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getInvitedBy() {
		return invitedBy;
	}
}
